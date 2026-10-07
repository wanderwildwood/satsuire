# Privacy

Wallet keeps your cards on the phone. It has no internet permission, so it cannot send them
anywhere; they leave the phone only when you share one, or save them to a file yourself.

That is the whole policy. The rest of this page is the evidence for it.

## One permission

`app/src/main/AndroidManifest.xml` declares exactly one:

```
android.permission.CAMERA
```

It is asked for the first time you press "Scan it with the camera", and used only while the
scanning screen is open. Refuse it and the app still works: type the number, or read the
barcode from a picture.

There is no `INTERNET` permission. A card number, a ticket, a boarding pass with your name in
it: none of it can reach a network from this app.

## What it keeps

The cards, in a database in the app's own storage, which no other app can read. It is
Catima's database, so a Catima export brought in keeps everything it carried. The settings
(the list's order and group, and the lock-screen switch) are in `SharedPreferences`.

The app is excluded from Android backups (`allowBackup="false"`).

## What leaves, and only when you ask

- **Share** hands one card's name, number and a picture of its barcode to the app you choose.
  The picture is written to the app's cache and that app is allowed to read that one file.
- **Save every card to a file** writes a Catima export where you choose. It has no password:
  anyone holding the file can read the cards in it.
- **Add to calendar** hands the card's name, days, number and note to your calendar app, which
  shows you the event before anything is saved.
- **Glance**, when it is installed and the switch in settings is on, is told the names of
  today's tickets, to show on the lock screen. Only Glance is answered; any other app that
  asks gets nothing.

## What comes in

A picture, PDF or pass file shared or opened here is read for its barcode and not kept. Only
the card you save from it stays.

## No analytics

No crash reporting, no telemetry, no advertising identifier. Catima's own crash reporter is
not part of this app. The dependencies are AndroidX, Jetpack Compose, Mudita's MMD, ZXing,
ZXing Android Embedded, Apache Commons CSV and zip4j.

## Checking any of this for yourself

```
aapt2 dump badging app-release.apk | grep uses-permission
```

prints every permission the built app carries:

```
uses-permission: name='android.permission.CAMERA'
uses-permission: name='com.wanderwildwood.satsuire.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

The second is not one of mine: AndroidX defines it for every app, signature-level and scoped
to this package, so a receiver registered at run time is not exposed to other apps. It grants
access to nothing.
