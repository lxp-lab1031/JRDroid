package jp.jrdroid;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercises the same cancellation and audio ownership used by the service. */
public final class SessionLifecycleTest {
    private static void check(boolean value) { if (!value) throw new AssertionError(); }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(2,TimeUnit.SECONDS)); }
    private static final class TestSocket extends Socket {
        final ByteArrayOutputStream commands=new ByteArrayOutputStream();
        final AtomicInteger closes=new AtomicInteger();
        CountDownLatch entered, release;
        volatile Thread ioThread;
        boolean throwOnWrite;
        public void shutdownInput() { ioThread=Thread.currentThread(); }
        public OutputStream getOutputStream() {
            if (throwOnWrite) throw new IllegalStateException("simulated close failure");
            return new OutputStream() {
                public void write(int value) throws IOException {
                    if (entered!=null) {
                        entered.countDown();
                        try { release.await(); } catch (InterruptedException e) { throw new InterruptedIOException(); }
                    }
                    commands.write(value);
                }
            };
        }
        public void close() { closes.incrementAndGet(); }
    }
    private static final class OwnedOutput implements PcmPlayback.Output {
        final Thread owner=Thread.currentThread();
        final AtomicInteger closes=new AtomicInteger();
        final CountDownLatch written=new CountDownLatch(1), waiting=new CountDownLatch(1);
        CountDownLatch closing, allowClose;
        boolean full;
        private void own() { check(Thread.currentThread()==owner); check(closes.get()==0); }
        public void play() { own(); }
        public void pause() { own(); }
        public void flush() { own(); }
        public long playedFrames() { own(); return 0; }
        public int write(short[] pcm,int offset,int count) { own(); written.countDown(); return full?0:count; }
        public void awaitSpace() throws InterruptedException { own(); waiting.countDown(); Thread.sleep(10000); }
        public void close() {
            own();
            if (closing!=null) {
                closing.countDown(); boolean interrupted=false;
                while (true) { try { allowClose.await(); break; } catch (InterruptedException e) { interrupted=true; } }
                if (interrupted) Thread.currentThread().interrupt();
            }
            closes.incrementAndGet();
        }
    }
    public static void main(String[] args) throws Exception {
        ReceiverSession transport=new ReceiverSession(); TestSocket socket=new TestSocket();
        socket.entered=new CountDownLatch(1); socket.release=new CountDownLatch(1);
        check(transport.attach(socket));
        Thread ui=new Thread(transport::requestStop,"UI-fixture"); ui.start();
        try {
            ui.join(500); check(!ui.isAlive()); await(socket.entered);
            check(socket.ioThread!=ui);
        } finally { socket.release.countDown(); }
        transport.requestStop(); transport.awaitStopped(); transport.requestStop();
        check(socket.closes.get()==1);
        byte[] command=socket.commands.toByteArray(); check(command.length==5 && command[0]==0x7e);
        System.out.println("PASS: stop returns while network I/O is blocked; driver exit/close execute once off caller");

        ReceiverSession startup=new ReceiverSession(); TestSocket starting=new TestSocket();
        starting.entered=new CountDownLatch(1); starting.release=new CountDownLatch(1);
        check(startup.attach(starting)); AtomicReference<Throwable> configureError=new AtomicReference<>();
        Thread configure=new Thread(() -> {
            try { startup.configure(starting,null,DChannels.DEFAULT_HZ); } catch (Throwable e) { configureError.set(e); }
        });
        configure.start(); await(starting.entered);
        try { startup.requestStop(); } finally { starting.release.countDown(); }
        startup.awaitStopped();configure.join(2000);check(!configure.isAlive());check(configureError.get()==null);
        DataInputStream ordered=new DataInputStream(new ByteArrayInputStream(starting.commands.toByteArray()));
        for (int opcode:new int[]{2,1,5,8,3,4,0x7e}) { check(ordered.readUnsignedByte()==opcode); ordered.readInt(); }
        check(ordered.available()==0 && starting.closes.get()==1);
        System.out.println("PASS: stop during driver configuration cannot interleave the frequency/control command bytes");

        ReceiverSession cancelled=new ReceiverSession(); cancelled.requestStop();
        TestSocket late=new TestSocket(); check(!cancelled.attach(late)); cancelled.awaitStopped();check(late.closes.get()==1);
        ReceiverSession newer=new ReceiverSession(); cancelled.requestStop(); check(newer.isActive()); newer.awaitStopped();
        ReceiverSession broken=new ReceiverSession(); TestSocket faulty=new TestSocket(); faulty.throwOnWrite=true;
        check(broken.attach(faulty)); broken.awaitStopped();check(broken.closeFailure!=null && faulty.closes.get()==1);
        System.out.println("PASS: cancelled startup and old cancellation cannot affect a new session; close errors are contained");

        AtomicReference<Throwable> failed=new AtomicReference<>();
        for (int i=0;i<200;i++) {
            ReceiverSession session=new ReceiverSession(); CountDownLatch opened=new CountDownLatch(1);
            AtomicReference<OwnedOutput> output=new AtomicReference<>(); final boolean full=i%3==2;
            session.startAudio(() -> {
                try { AudioPump.run(session,() -> {
                    OwnedOutput value=new OwnedOutput(); value.full=full; output.set(value); opened.countDown(); return value;
                }); }
                catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
                catch (Throwable e) { failed.compareAndSet(null,e); }
            });
            await(opened);
            if (i%3!=0) { session.offer(new short[320]); await(output.get().written); }
            if (full) await(output.get().waiting);
            session.requestStop(); session.requestStop(); session.awaitStopped();
            check(output.get().closes.get()==1);
            check(failed.get()==null);
        }
        System.out.println("PASS: 200 stop/restart cycles during idle, playback and full buffers; output closes on its owner");

        ReceiverSession slow=new ReceiverSession(); CountDownLatch opened=new CountDownLatch(1);
        CountDownLatch closing=new CountDownLatch(1), allowClose=new CountDownLatch(1);
        slow.startAudio(() -> {
            try { AudioPump.run(slow,() -> {
                OwnedOutput value=new OwnedOutput(); value.closing=closing; value.allowClose=allowClose; opened.countDown(); return value;
            }); }
            catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            catch (Throwable e) { failed.set(e); }
        });
        await(opened); slow.requestStop(); await(closing);
        Thread finisher=new Thread(slow::awaitStopped,"receiver-fixture");finisher.start();
        try { finisher.join(100);check(finisher.isAlive()); }
        finally { allowClose.countDown(); }
        finisher.join(2000);check(!finisher.isAlive());check(failed.get()==null);
        System.out.println("PASS: completion waits for audio release instead of releasing a live output after a timeout");
    }
}
