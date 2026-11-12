package com.tagtracker.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Einfache Übersetzung Deutsch/Englisch. Der Quelltext ist Englisch; I18n.t("...") liefert die
 * deutsche Fassung, wenn Deutsch gewählt ist. Standard: Sprache des Handys (Deutsch, sonst Englisch).
 */
final class I18n {
    private static final String KEY = "lang";        // "de", "en" oder leer = Handysprache
    private static volatile boolean de = Locale.getDefault().getLanguage().equals("de");
    private static final Map<String, String> DE = build();

    private I18n() {}

    /** Einmal beim App-Start aufrufen (TagApp.onCreate). */
    static void init(Context ctx) {
        de = resolve(ctx);
    }

    private static boolean resolve(Context ctx) {
        String v = TagApp.prefs(ctx).getString(KEY, "");
        if ("de".equals(v)) return true;
        if ("en".equals(v)) return false;
        return Locale.getDefault().getLanguage().equals("de");
    }

    static void set(Context ctx, String code) {
        SharedPreferences.Editor e = TagApp.prefs(ctx).edit();
        if ("de".equals(code) || "en".equals(code)) e.putString(KEY, code); else e.remove(KEY);
        e.apply();
        de = resolve(ctx);
    }

    static boolean de(Context ctx) {
        return de;
    }

    static String code(Context ctx) {
        return de ? "de" : "en";
    }

    static String t(Context ctx, String en) {
        return t(en);
    }

    static String t(String en) {
        if (de) {
            String v = DE.get(en);
            if (v != null) return v;
        }
        return en.replace("Tag Tracker", "Find All My Tags");
    }

    private static Map<String, String> build() {
        Map<String, String> m = new HashMap<>();
        m.put("  (no sync yet)",
              "  (noch nicht synchronisiert)");
        m.put("  Last sync: ",
              "  Letzte Synchronisierung: ");
        m.put("  ·  (failed)",
              "  ·  (fehlgeschlagen)");
        m.put(" is moving!",
              " bewegt sich!");
        m.put(" location(s) from the file",
              " Standort(e) aus der Datei");
        m.put(" location(s) from your Google Drive",
              " Standort(e) aus deinem Google Drive");
        m.put(" m. Tap to see the map.",
              " m bewegt. Tippen, um die Karte zu sehen.");
        m.put(" min",
              " Min.");
        m.put(" minutes, then sleeps.\n• It closes its network connection between checks.\n• Android (WorkManager) batches the checks at efficient moments.",
              " Minuten kurz auf und schläft dann.\n• Zwischen den Abfragen trennt sie die Netzwerkverbindung.\n• Android (WorkManager) bündelt die Abfragen zu günstigen Zeitpunkten.");
        m.put(" minutes.",
              " Minuten.");
        m.put(" new",
              " neu");
        m.put(" new location(s)",
              " neue(r) Standort(e)");
        m.put(" saved location(s) from Drive",
              " gespeicherte Standort(e) aus Drive");
        m.put(" seconds  (the rest of the day it's asleep)",
              " Sekunden  (den Rest des Tages schläft sie)");
        m.put(" tag(s) found",
              " Tag(s) gefunden");
        m.put(" · last check ",
              " · letzte Abfrage ");
        m.put(" · synced",
              " · synchronisiert");
        m.put("1. Connect your Google account",
              "1. Google-Konto verbinden");
        m.put("2. Background checking",
              "2. Abfrage im Hintergrund");
        m.put("3. Storage & sync",
              "3. Speichern & Synchronisieren");
        m.put("4. Your tags",
              "4. Deine Tags");
        m.put("5. Theft / guard alerts",
              "5. Diebstahl- / Wächter-Alarm");
        m.put("6. Tag batteries (Bluetooth)",
              "6. Akkustand der Tags (Bluetooth)");
        m.put("7. App lock (this phone only)",
              "7. App-Sperre (nur auf diesem Handy)");
        m.put("About this app",
              "Über diese App");
        m.put("Active",
              "Aktiv");
        m.put("Alerts you when a guarded tag moves",
              "Warnt dich, wenn sich ein bewachter Tag bewegt");
        m.put("Allow Bluetooth battery check",
              "Akku-Prüfung per Bluetooth erlauben");
        m.put("Allow unrestricted background (optional)",
              "Uneingeschränkten Hintergrundbetrieb erlauben (optional)");
        m.put("App lock set",
              "App-Sperre eingerichtet");
        m.put("App password",
              "App-Passwort");
        m.put("Automatic sync needs the Google sign-in first: ",
              "Die automatische Synchronisierung braucht zuerst die Google-Anmeldung: ");
        m.put("Average per check: ~",
              "Durchschnitt pro Abfrage: ~");
        m.put("Background",
              "Hintergrund");
        m.put("Background status notification is off.",
              "Statusmeldung im Hintergrund ist aus.");
        m.put("Background tracking stopped. Tap Resume to start again.",
              "Hintergrund-Ortung gestoppt. Tippe auf Fortsetzen, um wieder zu starten.");
        m.put("Battery & activity",
              "Akku & Aktivität");
        m.put("Battery & activity →",
              "Akku & Aktivität →");
        m.put("Battery status updated.",
              "Akkustand aktualisiert.");
        m.put("Bluetooth access is needed to read the batteries of tags near you.",
              "Für den Akkustand der Tags in deiner Nähe braucht die App Bluetooth-Zugriff.");
        m.put("Bluetooth is off or not allowed.",
              "Bluetooth ist aus oder nicht erlaubt.");
        m.put("Cancel",
              "Abbrechen");
        m.put("Cannot write to the Drive file",
              "Kann nicht in die Drive-Datei schreiben");
        m.put("Change app password",
              "App-Passwort ändern");
        m.put("Check for locations now",
              "Jetzt Standorte abfragen");
        m.put("Check tag batteries (Bluetooth)",
              "Akkustand der Tags prüfen (Bluetooth)");
        m.put("Checking Find Hub…",
              "Frage Find Hub ab …");
        m.put("Checks today: ",
              "Abfragen heute: ");
        m.put("Choose Google Drive file",
              "Google-Drive-Datei wählen");
        m.put("Choose a different file",
              "Andere Datei wählen");
        m.put("Close",
              "Schließen");
        m.put("Closing the app (swiping it away) does NOT stop tracking – it keeps checking and saving in the background, which is what you want. Use the button below only when you want to FULLY stop it.",
              "Wenn du die App schließt (wegwischst), läuft die Ortung trotzdem weiter – sie fragt im Hintergrund ab und speichert, genau so soll es sein. Den Knopf unten brauchst du nur, wenn du sie KOMPLETT stoppen willst.");
        m.put("Connected ",
              "Verbunden: ");
        m.put("Could not list tags: ",
              "Tags konnten nicht geladen werden: ");
        m.put("Couldn't finish sign-in",
              "Anmeldung nicht abgeschlossen");
        m.put("Currently every ",
              "Derzeit alle ");
        m.put("DEFAULT: automatic sync to a ‘TagTracker’ folder in your own Google account – no file picking, and it comes back automatically when you sign in on another phone.",
              "STANDARD: automatische Synchronisierung in einen Ordner ‘TagTracker’ in deinem eigenen Google-Konto – ohne Dateiauswahl, und sie kommt automatisch zurück, wenn du dich auf einem anderen Handy anmeldest.");
        m.put("Default sync on – Drive folder ready",
              "Standard-Sync an – Drive-Ordner bereit");
        m.put("Default sync on – pulled ",
              "Standard-Sync an – geholt: ");
        m.put("Drive sync set up",
              "Drive-Synchronisierung eingerichtet");
        m.put("Encryption unlocked",
              "Verschlüsselung entsperrt");
        m.put("Enter app password",
              "App-Passwort eingeben");
        m.put("Export failed: ",
              "Export fehlgeschlagen: ");
        m.put("Find Hub shows only your tag's last spot. This app saves the full history and alerts you if it moves. New here? Read the quick guide first – it explains everything.",
              "Find Hub zeigt nur den letzten Ort deiner Tags. Diese App speichert den ganzen Verlauf und warnt dich, wenn sich ein Tag bewegt. Neu hier? Lies zuerst die kurze Anleitung – sie erklärt alles.");
        m.put("Forgot? Show hint",
              "Vergessen? Hinweis zeigen");
        m.put("Guard mode is off.",
              "Wächter-Modus ist aus.");
        m.put("Help",
              "Hilfe");
        m.put("Hide background notification",
              "Statusmeldung im Hintergrund ausblenden");
        m.put("Hint: ",
              "Hinweis: ");
        m.put("How it works",
              "So funktioniert's");
        m.put("Import failed: ",
              "Import fehlgeschlagen: ");
        m.put("Import login file (google_secrets.json)",
              "Login-Datei importieren (google_secrets.json)");
        m.put("Legal & privacy",
              "Rechtliches & Datenschutz");
        m.put("Legal, disclaimer & privacy",
              "Rechtliches, Haftung & Datenschutz");
        m.put("Loading…",
              "Lädt …");
        m.put("Location updated – saved to history",
              "Standort aktualisiert – im Verlauf gespeichert");
        m.put("Login imported – signed in and unlocked. Tap Check now.",
              "Login importiert – angemeldet und entsperrt. Tippe auf Jetzt abfragen.");
        m.put("Login imported, but it has no encryption key (unlock part). Locations may not decrypt.",
              "Login importiert, aber ohne Verschlüsselungsschlüssel (Entsperr-Teil). Standorte lassen sich eventuell nicht entschlüsseln.");
        m.put("Longer interval = less battery. 240 min is the battery-saver option. See exactly what the app does and its tiny active time in Battery & activity:",
              "Längeres Intervall = weniger Akkuverbrauch. 240 Min. ist die Stromspar-Variante. Was die App genau tut und wie kurz sie aktiv ist, siehst du unter Akku & Aktivität:");
        m.put("Moved ",
              "Hat sich ");
        m.put("NOT AFFILIATED WITH GOOGLE\nTag Tracker is an independent, open-source app. It is not created, endorsed or supported by Google. “Google”, “Find Hub” and “Android” are trademarks of Google LLC.\n\nUNOFFICIAL METHOD / YOUR RISK\nIt reads the Find network using the open-source GoogleFindMyTools, an unofficial method. Google provides no public API and may change or block this at any time. Using an unofficial client may violate Google's Terms of Service. You use this app at your own risk, including risk to your Google account.\n\nACCEPTABLE USE\nTrack only trackers you own or are authorised to track. Do NOT use it to locate or monitor any person without their knowledge and consent – that is illegal in many places. If something is stolen, give the location to the police; do not confront anyone.\n\nPRIVACY\nYour history stays on your phone and, if enabled, in your own Google Drive. The app has no servers of its own and sends no data to the developer or any third party. Your Google tokens stay on your device.\n\nNO WARRANTY\nProvided “AS IS”, without warranty of any kind. The authors are not liable for any damages, data loss, account issues, or missed/incorrect locations.\n\nLICENSE\nGNU GPL v3.0. Built on GoogleFindMyTools by Leon Böttger (GPL-3.0).",
              "KEINE VERBINDUNG ZU GOOGLE\nFind All My Tags ist eine unabhängige Open-Source-App. Sie wird von Google weder erstellt noch empfohlen oder unterstützt. „Google“, „Find Hub“ und „Android“ sind Marken der Google LLC.\n\nINOFFIZIELLE METHODE / AUF EIGENES RISIKO\nDie App liest das Find-Netzwerk mit dem Open-Source-Projekt GoogleFindMyTools, einer inoffiziellen Methode. Google bietet dafür keine öffentliche Schnittstelle und kann sie jederzeit ändern oder sperren. Ein inoffizieller Zugang kann gegen die Nutzungsbedingungen von Google verstoßen. Du nutzt die App auf eigenes Risiko, auch was dein Google-Konto betrifft.\n\nERLAUBTE NUTZUNG\nOrte nur Tracker, die dir gehören oder die du orten darfst. Verwende die App NICHT, um Personen ohne ihr Wissen und ihre Zustimmung zu orten oder zu überwachen – das ist vielerorts verboten. Wurde etwas gestohlen, gib den Standort an die Polizei weiter; stell niemanden selbst zur Rede.\n\nDATENSCHUTZ\nDein Verlauf bleibt auf deinem Handy und, falls eingeschaltet, in deinem eigenen Google Drive. Die App hat keine eigenen Server und schickt keine Daten an den Entwickler oder an Dritte. Deine Google-Zugangsdaten bleiben auf deinem Gerät.\n\nKEINE GEWÄHR\nBereitgestellt „wie besehen“, ohne jede Gewährleistung. Die Autoren haften nicht für Schäden, Datenverlust, Probleme mit dem Konto oder fehlende bzw. falsche Standorte.\n\nLIZENZ\nGNU GPL v3.0. Basiert auf tag-tracker von BKrepublic und GoogleFindMyTools von Leon Böttger (beide GPL-3.0).");
        m.put("New app password (min 4)",
              "Neues App-Passwort (mind. 4 Zeichen)");
        m.put("No Google Drive file chosen",
              "Keine Google-Drive-Datei gewählt");
        m.put("No checks logged yet. They appear here after the app runs in the background.",
              "Noch keine Abfragen protokolliert. Sie erscheinen hier, sobald die App im Hintergrund läuft.");
        m.put("No file picker available",
              "Keine Dateiauswahl verfügbar");
        m.put("No hint was set for this phone.",
              "Für dieses Handy wurde kein Hinweis festgelegt.");
        m.put("No new location right now",
              "Gerade kein neuer Standort");
        m.put("No tag in Bluetooth range right now.",
              "Gerade kein Tag in Bluetooth-Reichweite.");
        m.put("Not affiliated with Google. This app uses an UNOFFICIAL method to read the Find network – Google may block it at any time, and it may violate Google's Terms of Service. For tracking YOUR OWN property only, never a person without consent. Your data stays on your phone and your own Google Drive; nothing is sent to the developer. Provided AS IS, no warranty. Open-source (GPL-3.0), built on GoogleFindMyTools.",
              "Keine Verbindung zu Google. Diese App nutzt eine INOFFIZIELLE Methode, um das Find-Netzwerk zu lesen – Google kann sie jederzeit sperren, und sie kann gegen die Nutzungsbedingungen von Google verstoßen. Nur für DEIN EIGENES Eigentum, niemals für Personen ohne deren Zustimmung. Deine Daten bleiben auf deinem Handy und in deinem eigenen Google Drive; nichts geht an den Entwickler. Ohne Gewähr. Open Source (GPL-3.0), basiert auf tag-tracker und GoogleFindMyTools.");
        m.put("Note: ",
              "Hinweis: ");
        m.put("Open Android battery settings for this app",
              "Android-Akkueinstellungen für diese App öffnen");
        m.put("Open app permissions",
              "App-Berechtigungen öffnen");
        m.put("Open in Maps",
              "In Maps öffnen");
        m.put("Open map",
              "Karte öffnen");
        m.put("Option B: If the steps above fail, run the desktop setup once on a PC (python -m server.manage google-login), copy the resulting data/google_secrets.json to this phone, and import it here. No re-login needed on the phone.",
              "Option B: Wenn die Schritte oben nicht klappen, führe einmal die Desktop-Einrichtung am PC aus (python -m server.manage google-login), kopiere die entstandene Datei data/google_secrets.json auf dieses Handy und importiere sie hier. Am Handy ist dann keine neue Anmeldung nötig.");
        m.put("Optional: only if your phone (Xiaomi/Samsung/etc.) aggressively kills background apps, allow unrestricted running so checks aren't skipped. Not required on most phones.",
              "Optional: Nur wenn dein Handy (Xiaomi/Samsung usw.) Hintergrund-Apps aggressiv beendet, erlaube den uneingeschränkten Betrieb, damit keine Abfragen ausfallen. Auf den meisten Handys nicht nötig.");
        m.put("Password too short",
              "Passwort zu kurz");
        m.put("Phone battery & activity",
              "Handy-Akku & Aktivität");
        m.put("Protect the app with a password so no one who picks up your phone can open your tracker. The password and hint are stored ONLY on this phone – never sent to Google or Drive, so they can't be recovered from anywhere else. If you forget it and the hint doesn't help, reinstall the app.",
              "Schütze die App mit einem Passwort, damit niemand, der dein Handy in die Hand bekommt, deinen Tracker öffnen kann. Passwort und Hinweis werden NUR auf diesem Handy gespeichert – nie bei Google oder in Drive, deshalb lassen sie sich nirgends wiederherstellen. Wenn du das Passwort vergisst und der Hinweis nicht hilft, installiere die App neu.");
        m.put("Read full disclaimer & privacy",
              "Haftungsausschluss & Datenschutz lesen");
        m.put("Recent checks",
              "Letzte Abfragen");
        m.put("Recovery hint (e.g. ‘usual PIN’)",
              "Erinnerungshinweis (z. B. ‘übliche PIN’)");
        m.put("Refresh",
              "Neu laden");
        m.put("Refresh tag list from Google",
              "Tag-Liste von Google neu laden");
        m.put("Replace the battery soon, or the tag will stop sending its location.",
              "Tausche die Batterie bald, sonst meldet der Tag keinen Standort mehr.");
        m.put("Restore failed: ",
              "Wiederherstellen fehlgeschlagen: ");
        m.put("Restore history from a Drive file",
              "Verlauf aus einer Drive-Datei wiederherstellen");
        m.put("Restored ",
              "Wiederhergestellt: ");
        m.put("Resume background tracking",
              "Hintergrund-Ortung fortsetzen");
        m.put("Ring tag",
              "Tag klingeln lassen");
        m.put("Ringing the tag…",
              "Tag klingelt …");
        m.put("Run \"Check for locations now\" once first, then try again.",
              "Führe zuerst einmal „Jetzt Standorte abfragen“ aus und versuch es dann noch einmal.");
        m.put("Save",
              "Speichern");
        m.put("Save or share ",
              "Speichern oder teilen: ");
        m.put("Searching for tags nearby (10 s)…",
              "Suche Tags in der Nähe (10 s) …");
        m.put("Set an app password",
              "App-Passwort festlegen");
        m.put("Set app password",
              "App-Passwort festlegen");
        m.put("Set up Tag Tracker",
              "Find All My Tags einrichten");
        m.put("Setup & settings",
              "Einrichtung & Einstellungen");
        m.put("Share location",
              "Standort teilen");
        m.put("Show background notification",
              "Statusmeldung im Hintergrund anzeigen");
        m.put("Shows when the app checks Find Hub and syncs to Google Drive",
              "Zeigt an, wenn die App Find Hub abfragt und mit Google Drive synchronisiert");
        m.put("Sign in",
              "Anmelden");
        m.put("Sign in again",
              "Erneut anmelden");
        m.put("Sign in again to unlock encryption",
              "Melde dich erneut an, um die Verschlüsselung zu entsperren");
        m.put("Sign in to Google",
              "Bei Google anmelden");
        m.put("Sign-in didn't complete. To protect your account the app stopped retrying. You can try again, or use the more reliable Option B: import a login file made on a PC (see Setup).",
              "Die Anmeldung wurde nicht abgeschlossen. Zum Schutz deines Kontos versucht die App es nicht weiter. Du kannst es noch einmal probieren oder die zuverlässigere Option B nutzen: eine am PC erstellte Login-Datei importieren (siehe Einrichtung).");
        m.put("Something went wrong",
              "Etwas ist schiefgelaufen");
        m.put("Step 1: Sign in",
              "Schritt 1: Anmelden");
        m.put("Step 2: Unlock encryption (needed to see locations)",
              "Schritt 2: Verschlüsselung entsperren (nötig, um Standorte zu sehen)");
        m.put("Sync default folder now",
              "Standardordner jetzt synchronisieren");
        m.put("Sync failed: ",
              "Synchronisierung fehlgeschlagen: ");
        m.put("Sync status",
              "Sync-Status");
        m.put("Sync to Drive now",
              "Jetzt mit Drive synchronisieren");
        m.put("Synced to Drive",
              "Mit Drive synchronisiert");
        m.put("Tag Tracker",
              "Find All My Tags");
        m.put("Tag Tracker is built to sip battery:\n• It uses NO GPS (positions come from Google's network). Location access is only used for the optional Bluetooth battery check.\n• It wakes only briefly every ",
              "Find All My Tags ist auf sparsamen Akkuverbrauch ausgelegt:\n• Kein GPS (die Positionen kommen aus dem Google-Netzwerk). Standortzugriff dient nur der optionalen Akku-Prüfung der Tags per Bluetooth.\n• Die App wacht nur alle ");
        m.put("Tag Tracker is running",
              "Find All My Tags läuft");
        m.put("Tag Tracker setup",
              "Find All My Tags einrichten");
        m.put("Tag battery",
              "Akkustand der Tags");
        m.put("Tags send their battery level (normal / low / critical) over Bluetooth. The app can only read it when a tag is near this phone – then you get a notification if a battery gets low. Exact percentages are not sent by the tags. Run “Check for locations now” once first.",
              "Tags senden ihren Akkustand (normal / niedrig / kritisch) per Bluetooth. Die App kann ihn nur lesen, wenn ein Tag in der Nähe dieses Handys ist – dann bekommst du eine Meldung, sobald ein Akku schwach wird. Genaue Prozentwerte senden die Tags nicht. Führe zuerst einmal „Jetzt Standorte abfragen“ aus.");
        m.put("Tells you when a tag's battery gets low",
              "Meldet dir, wenn der Akku eines Tags schwach wird");
        m.put("The app checks Find Hub in the background with Android WorkManager (Google's battery-efficient scheduler) and saves every location. It uses almost no battery while idle: it only wakes for a few seconds per check, needs a network connection, and closes the connection in between. It uses no GPS (locations come from Google's network). Location access is only used for the optional Bluetooth battery check. Android's minimum interval is 15 minutes; pick a longer one to use even less battery.",
              "Die App fragt Find Hub im Hintergrund über Android WorkManager ab (Googles stromsparender Zeitplaner) und speichert jeden Standort. Im Ruhezustand braucht sie kaum Akku: Sie wacht pro Abfrage nur ein paar Sekunden auf, braucht eine Internetverbindung und trennt sie dazwischen wieder. Kein GPS (die Standorte kommen aus dem Google-Netzwerk). Standortzugriff dient nur der optionalen Akku-Prüfung per Bluetooth. Android erlaubt mindestens 15 Minuten Abstand; ein längeres Intervall spart noch mehr Akku.");
        m.put("The battery is getting weak. Have a spare ready.",
              "Die Batterie wird schwach. Halte eine Ersatzbatterie bereit.");
        m.put("Theft / movement alerts",
              "Diebstahl- / Bewegungsalarm");
        m.put("To also check in the background, set location access for this app to “Allow all the time”.",
              "Damit auch im Hintergrund geprüft wird, stell den Standortzugriff dieser App auf „Immer zulassen“.");
        m.put("Today",
              "Heute");
        m.put("Total active time today: ~",
              "Aktive Zeit heute insgesamt: ~");
        m.put("Tracking resumed",
              "Ortung fortgesetzt");
        m.put("Turn OFF guard mode",
              "Wächter-Modus AUSSCHALTEN");
        m.put("Turn ON guard mode",
              "Wächter-Modus EINSCHALTEN");
        m.put("Turn off Drive sync",
              "Drive-Synchronisierung ausschalten");
        m.put("Turn off app lock",
              "App-Sperre ausschalten");
        m.put("Turn off automatic sync",
              "Automatische Synchronisierung ausschalten");
        m.put("Turn this ON when the bike is parked. If a tag MOVES more than ~150 m, you get a LOUD notification with a Maps link – so you can act fast if it's stolen. Uses the same background checks, so no extra battery. Tip: if it's stolen, share the live location and history with the police – don't confront the thief yourself.",
              "Schalte das EIN, wenn das Rad abgestellt ist. Bewegt sich ein Tag mehr als ~150 m, bekommst du eine LAUTE Meldung mit Maps-Link – so kannst du bei einem Diebstahl schnell handeln. Nutzt dieselben Hintergrund-Abfragen, also kein zusätzlicher Akkuverbrauch. Tipp: Bei einem Diebstahl teile Live-Standort und Verlauf mit der Polizei – stell den Dieb nicht selbst.");
        m.put("Two quick steps on Google's own pages (the app never sees your password or PIN). Do both – locations stay hidden until step 2 is done.",
              "Zwei kurze Schritte auf Googles eigenen Seiten (die App sieht dein Passwort oder deine PIN nie). Mach beide – ohne Schritt 2 bleiben die Standorte verborgen.");
        m.put("Unlock",
              "Entsperren");
        m.put("Unlock Find Hub encryption",
              "Find-Hub-Verschlüsselung entsperren");
        m.put("Unlock again",
              "Erneut entsperren");
        m.put("Unlock encryption keys",
              "Verschlüsselungsschlüssel entsperren");
        m.put("Use automatic default sync (recommended)",
              "Automatische Standard-Synchronisierung nutzen (empfohlen)");
        m.put("Very low battery use",
              "Sehr geringer Akkuverbrauch");
        m.put("Wrong password",
              "Falsches Passwort");
        m.put("Your locations are ALWAYS saved on the phone first – they can never be lost, even with no internet or if Drive is busy. Google Drive is an extra cloud copy on top. Choose:\n• CONTINUOUS SYNC (recommended): phone + Google Drive, syncs after each check. If offline or rate-limited, it just retries later – nothing is lost.\n• LOCAL ONLY: keep everything on the phone, no cloud (turn both options below off).",
              "Deine Standorte werden IMMER zuerst auf dem Handy gespeichert – sie gehen nie verloren, auch ohne Internet oder wenn Drive gerade beschäftigt ist. Google Drive ist eine zusätzliche Kopie in der Cloud. Wähle:\n• LAUFENDE SYNCHRONISIERUNG (empfohlen): Handy + Google Drive, nach jeder Abfrage. Wenn offline oder gebremst, wird es später einfach erneut versucht – nichts geht verloren.\n• NUR LOKAL: alles bleibt am Handy, keine Cloud (beide Optionen unten ausschalten).");
        m.put("active in background",
              "läuft im Hintergrund");
        m.put("battery critically low",
              "Akku kritisch");
        m.put("battery low",
              "Akku schwach");
        m.put("— or use a CUSTOM file (any account / location) —\nIn the picker, tap ☰ and choose GOOGLE DRIVE (not phone storage), pick a folder, and name it e.g. TagTracker-history.csv.",
              "— oder eine EIGENE Datei verwenden (beliebiges Konto / beliebiger Ort) —\nTippe in der Auswahl auf ☰, wähle GOOGLE DRIVE (nicht den Handyspeicher), such einen Ordner aus und nenn die Datei z. B. TagTracker-history.csv.");
        m.put("— or —",
              "— oder —");
        m.put("⏸ Background tracking is STOPPED. No locations are being saved.",
              "⏸ Hintergrund-Ortung ist GESTOPPT. Es werden keine Standorte gespeichert.");
        m.put("⏹ Stop tracking (full exit)",
              "⏹ Ortung stoppen (komplett beenden)");
        m.put("⚠ You're signed in and your tags are listed, but the map will stay empty until you finish step 2. Tap “Unlock encryption keys” and enter your phone's screen lock.",
              "⚠ Du bist angemeldet und deine Tags sind aufgelistet, aber die Karte bleibt leer, bis du Schritt 2 erledigst. Tippe auf „Verschlüsselungsschlüssel entsperren“ und gib die Bildschirmsperre deines Handys ein.");
        m.put("✓ A status notification shows while the app runs in the background.",
              "✓ Während die App im Hintergrund läuft, wird eine Statusmeldung angezeigt.");
        m.put("✓ All set – the app can read and decrypt your tags.",
              "✓ Alles bereit – die App kann deine Tags lesen und entschlüsseln.");
        m.put("✓ Also checked in the background with every location check.",
              "✓ Wird auch im Hintergrund bei jeder Standortabfrage geprüft.");
        m.put("✓ App lock is ON.",
              "✓ App-Sperre ist AN.");
        m.put("✓ Auto-syncing to your Drive file.",
              "✓ Synchronisiert automatisch in deine Drive-Datei.");
        m.put("✓ Battery check runs whenever you open the app.",
              "✓ Akku-Prüfung läuft jedes Mal, wenn du die App öffnest.");
        m.put("✓ DEFAULT automatic sync is ON (folder ‘TagTracker’ in your Google account).",
              "✓ STANDARD-Synchronisierung ist AN (Ordner ‘TagTracker’ in deinem Google-Konto).");
        m.put("✓ Guard mode is ON – you'll be alerted if a tag moves.",
              "✓ Wächter-Modus ist AN – du wirst gewarnt, wenn sich ein Tag bewegt.");
        m.put("✓ Unrestricted background running is allowed.",
              "✓ Uneingeschränkter Hintergrundbetrieb ist erlaubt.");
        m.put("📖  How it works (guide)",
              "📖  So funktioniert's (Anleitung)");
        m.put("🔒 Guarding",
              "🔒 Wache");
        return m;
    }
}
