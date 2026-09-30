# Project Map
_Generated: 2026-05-12 22:44 | Staleness: timestamps_

## Directory Structure
app/ — Android Compose aplikacija za kalorije, makroe, namirnice, težinu, ciljeve, TRT/cycle kontekst i backup.
app/src/main/java/com/kalorije/app/data/model/ — domen modeli hrane, obroka, ciljeva, profila, težine i insight-a.
app/src/main/java/com/kalorije/app/data/db/ — Room baza, entiteti, DAO-i, mapper-i i JSON-to-Room migracija.
app/src/main/java/com/kalorije/app/data/repository/ — `FoodRepository` kao centralni persistence i state sloj.
app/src/main/java/com/kalorije/app/ui/screens/ — home, unos hrane, istorija, podešavanja, shake i weight progress ekrani.
app/src/main/java/com/kalorije/app/notification/ — dnevni reminder receiver-i i scheduling.
app/src/test/ — fokusirani testovi za grupisanje obroka.

## Key Files
settings.gradle.kts — definiše Gradle root `Kalorije`.
app/build.gradle.kts — Compose, Room/KSP, notification i test konfiguracija.
app/src/main/AndroidManifest.xml — internet, network, notification, exact alarm i boot receiver permisije.
app/src/main/java/com/kalorije/app/MainActivity.kt — ulaz u Compose UI.
app/src/main/java/com/kalorije/app/data/repository/FoodRepository.kt — centralni tok za namirnice, unose, profile, težinu i backup.
app/src/main/java/com/kalorije/app/data/db/KalorijeDatabase.kt — Room baza.
app/src/main/java/com/kalorije/app/data/db/JsonToRoomMigration.kt — migracija starog JSON formata.
app/src/main/java/com/kalorije/app/ui/navigation/NavGraph.kt — Compose navigacija.
app/src/main/java/com/kalorije/app/util/MealGrouping.kt — logika grupisanja obroka preko ponoći.
app/src/main/java/com/kalorije/app/util/WeightNormalizer.kt — normalizacija težine i TRT/cycle korekcije.
weight-algorithm.md — dokumentacija algoritma za težinu.

## Critical Constraints
- Poštovati parent `AGENTS.md`: bez Git komandi, čak i ako postoji `.git` folder.
- Promene modela i Room sloja moraju ostati kompatibilne sa JSON migracijom i Google Drive backup-om.
- Logički datum i grupisanje obroka preko ponoći su osetljivi tokovi; proveravati `MealGroupingTest`.
- Weight progress logika zavisi od normalizacije, ciljeva i TRT konteksta; ne menjati samo UI bez provere util/ViewModel sloja.

## Hot Files
app/src/main/java/com/kalorije/app/data/repository/FoodRepository.kt, app/src/main/java/com/kalorije/app/data/db/KalorijeDatabase.kt, app/src/main/java/com/kalorije/app/ui/navigation/NavGraph.kt, app/src/main/java/com/kalorije/app/util/MealGrouping.kt, app/src/main/java/com/kalorije/app/util/WeightNormalizer.kt
