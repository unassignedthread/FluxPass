# FluxPass

GUI password manager for [pass](https://www.passwordstore.org/), the standard Unix password manager.

## Features

- Browse and search password store entries in a tree view
- View, copy, and toggle password visibility
- Add entries with automatic path generation from URLs
- Type-based creation: Website (URL→path, login), Note (title+content), Generic
- Password generation with configurable length and symbol options
- System tray support (X11, minimize to tray)
- Dark and light themes (dark by default)
- Automatic clipboard clearing after 30 seconds
- Parsed metadata display (Login, URL, Title, User, etc.)

## Requirements

- Java 17 or later
- [pass](https://www.passwordstore.org/) (the standard Unix password manager)
- GPG configured with at least one key
- Maven (for building)

## Build

```bash
mvn package
```

The fat JAR is written to `target/fluxpass.jar`.

## Run

```bash
# Using the JavaFX Maven plugin
mvn javafx:run

# Using the fat JAR
java -jar target/fluxpass.jar

# Using the launch script
./fluxpass.sh
```

## Install (Manual)

```bash
sudo cp fluxpass.sh /usr/bin/fluxpass
sudo chmod +x /usr/bin/fluxpass
sudo cp fluxpass.desktop /usr/share/applications/
sudo cp target/fluxpass.jar /usr/share/java/fluxpass/
```

## AUR

PKGBUILD is included in this repository.

```bash
git clone https://github.com/unassignedthread/FluxPass.git
cd FluxPass
makepkg -si
```

Or install from AUR:

```bash
yay -S fluxpass
```

## License

MIT
