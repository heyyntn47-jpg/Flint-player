# App icon

The Flint icon is already in place:

- `res/mipmap-anydpi-v26/ic_launcher.xml`: adaptive icon (Android 8+) with background, foreground and monochrome layers
- `res/mipmap-*/ic_launcher_foreground.png` and `ic_launcher_monochrome.png`: the logo layers, 108dp at each density
- `res/mipmap-*/ic_launcher.png`: plain icon for Android 7.x
- `res/values/ic_launcher_background.xml`: the background color (`#000000`)

To change it, replace the PNGs (keep the file names and sizes: 108, 162, 216, 324, 432 px for the
foreground/monochrome layers; 48, 72, 96, 144, 192 px for `ic_launcher.png`) and edit the
background color. Keep the logo inside the centre 66% of the foreground layer so round masks
don't crop it.
