# bluetoothle-codenameone

Bluethooth LE Library for [Codename One](https://github.com/codenameone/CodenameOne) apps.
This library now uses a native Codename One bridge implementation adapted from the original Bluetooth LE plugin lineage.

## Integration

This library can be added to a Codename One project in either of these ways.

### Maven-based Codename One project

Add the library dependency to your project `pom.xml`:

```xml
<dependency>
    <groupId>com.codenameone</groupId>
    <artifactId>cn1-bluetooth-lib</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
</dependency>
```

This is the same dependency form used by the sample app in [BTDemo/pom.xml](BTDemo/pom.xml).

### Classic Codename One project using `.cn1lib`

1. Build or download the `.cn1lib` artifact for this library.
2. Add the `.cn1lib` file into your project's `lib/` directory.
3. In the Codename One project, run `Refresh Libs`.
4. Clean and rebuild the project.

The library requires Java 8, as declared in [common/codenameone_library_required.properties](common/codenameone_library_required.properties).

## API Reference

- See [docs/API.md](docs/API.md) for call-by-call API documentation and behavior notes.

## Sample of Usage

```java
        final Bluetooth bt = new Bluetooth();
        Form main = new Form("Bluetooth Demo");
        main.setLayout(new BoxLayout(BoxLayout.Y_AXIS));
        main.add(new Button(new Command("enable bluetooth") {

            @Override
            public void actionPerformed(ActionEvent evt) {

                try {
                    if (!bt.isEnabled()) {
                        bt.enable();
                    }
                    if (!bt.hasPermission()) {
                        bt.requestPermission();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }));
        main.add(new Button(new Command("initialize") {

            @Override
            public void actionPerformed(ActionEvent evt) {
                try {
                    bt.initialize(true, false, "bluetoothleplugin");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }));
```

## Credits

1. Steve Hannah - for the https://github.com/shannah/CN1JSON
2. Rand Dusing - for the original bluetoothle plugin lineage
