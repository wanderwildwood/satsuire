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
- **Save every card to a file** writes a Catima export where you choose. Given a password it
  is locked with AES inside the zip, as Catima locks its own, and only that password opens it;
  given none, it is a plain file anyone holding it can read.
- **Add to calendar** hands the card's name, days, number and note to Datebook, and to no other
  calendar app; Datebook shows you the event before anything is saved. It is also given a link
  back to the card.
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

<!-- shared-about privacy section -->
## Who we are

Wallet is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Wallet keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Wallet is sold or shared.

Since Wallet keeps what it stores only on your phone, you can exercise most of these rights yourself: edit or delete a card in the app, take a copy with Save every card to a file, clear the app's data in Android's settings, or uninstall the app, which deletes all of it. For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->
