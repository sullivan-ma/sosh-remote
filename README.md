# Sosh Remote — App Android

Télécommande WiFi pour décodeur Sosh (basé Livebox Play / Orange).

## 📦 Compiler l'APK — Méthode simple (en ligne, gratuit)

### Option A : Replit (recommandé, sans rien installer)

1. Va sur **https://replit.com** → créer un compte gratuit
2. Clique **Create Repl** → choisir **Android (Gradle)**
3. Glisse-dépose tout le contenu du dossier `SoshRemote/` dans Replit
4. Dans la console : `./gradlew assembleDebug`
5. L'APK sera dans `app/build/outputs/apk/debug/app-debug.apk`
6. Télécharge-le et installe-le sur ton téléphone

### Option B : GitHub Actions (automatique)

1. Crée un dépôt GitHub (gratuit) et pousse ce projet
2. Crée le fichier `.github/workflows/build.yml` :

```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build APK
        run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v3
        with:
          name: sosh-remote.apk
          path: app/build/outputs/apk/debug/app-debug.apk
```

3. Pousse le code → GitHub compile automatiquement → télécharge l'APK dans "Actions"

### Option C : Android Studio (PC/Mac)

1. Installer Android Studio : https://developer.android.com/studio
2. Ouvrir le dossier `SoshRemote`
3. Build → Generate Signed Bundle/APK → APK → Next → Debug
4. L'APK est dans `app/build/outputs/apk/debug/`

## 📱 Installer l'APK sur Android

1. Sur ton téléphone : **Paramètres → Sécurité → Sources inconnues** → Activer
2. Transfère l'APK sur le téléphone (USB, email, cloud)
3. Ouvre le fichier APK → Installer

## 🔌 Utilisation

1. Lance l'app
2. Appuie sur **"Rechercher les décodeurs"** — l'app scanne le réseau WiFi
3. Ou entre manuellement l'IP du décodeur (visible dans ta Livebox sur 192.168.1.1)
4. Clique **Connecter** puis utilise la télécommande !

## 🛠 Architecture

- **API** : `http://<IP>/remoteControl/cmd?operation=01&key=<code>&mode=0`
- **Scanner** : Scan parallèle (40 threads) des 254 IPs du subnet local
- **Détection subnet** : Via `NetworkInterface` Android natif
- **HTTP** : OkHttp3 pour les requêtes réseau
