# bluetoothle-codenameone

Bluethooth LE Library for [Codename One](https://github.com/codenameone/CodenameOne) apps.
This library now uses a native Codename One bridge implementation adapted from the original Bluetooth LE plugin lineage.

## Integration

1. Build the project
2. Place the CN1Bluethooth.cn1lib file in your CN1 project lib.
3. Add the CN1JSON.cn1lib file in your CN1 project lib. (https://github.com/shannah/CN1JSON/)
4. Right click on your CN1 project and select "Refresh Libs" then clean build your project.

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
