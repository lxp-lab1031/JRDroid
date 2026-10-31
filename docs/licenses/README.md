# Additional upstream license files

`jni-sys-macros` 0.4.1's crates.io archive declares MIT OR Apache-2.0 but does not contain the license files. The two files in this directory were fetched from its declared upstream repository at the exact commit recorded in its `.cargo_vcs_info.json`:

https://github.com/jni-rs/jni-sys/tree/64d77b7a5f119d7b55b4e2c169a4668067ff59e6

These are included in the APK's generated third-party notices. Other dependency notices come from the frozen Cargo packages.

`rust-1.90.0-standard-library.html` is the unmodified copyright and license notice shipped with the official Rust 1.90.0 toolchain (`share/doc/rust/COPYRIGHT-library.html`). Its text is also included in the APK's notices.
