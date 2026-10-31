package jp.lxplab.jrdroid;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import jp.lxplab.jrdroid.receiver.ReceiverConfig;

/** Lists USB descriptors only; never claims an interface or starts a receiver. */
public final class MainActivity extends Activity {
    private TextView usbStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);
        // Keep content clear of system bars when targeting Android 15+.
        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(),
                    padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(),
                    padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title = new TextView(this);
        ReceiverConfig config = new ReceiverConfig(ReceiverConfig.DEFAULT_FREQUENCY_HZ);
        title.setText(String.format(Locale.JAPAN,
                "JRDroid\n対象周波数：%.4f MHz\n%s\n\n",
                config.getFrequencyHz() / 1_000_000.0, getString(R.string.receiver_status)));
        layout.addView(title);
        Button refresh = new Button(this);
        refresh.setText(R.string.refresh_usb);
        refresh.setOnClickListener(view -> refreshUsbDevices());
        layout.addView(refresh);
        usbStatus = new TextView(this);
        layout.addView(usbStatus);
        setContentView(layout);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsbDevices();
    }

    private void refreshUsbDevices() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            usbStatus.setText("USBホスト機能がありません。");
            return;
        }
        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        if (manager == null) {
            usbStatus.setText("USBサービスを利用できません。");
            return;
        }
        List<UsbDevice> devices = new ArrayList<>(manager.getDeviceList().values());
        devices.sort(Comparator.comparing(UsbDevice::getDeviceName));
        if (devices.isEmpty()) {
            usbStatus.setText("USB機器が見つかりません。接続・変換アダプター・給電を確認してください。");
            return;
        }
        StringBuilder text = new StringBuilder("接続中のUSB機器（RTL-SDRの対応判定は未実装）\n");
        for (UsbDevice device : devices) {
            text.append(String.format(Locale.ROOT, "VID:%04X PID:%04X\n",
                    device.getVendorId(), device.getProductId()));
        }
        usbStatus.setText(text.toString());
    }
}
