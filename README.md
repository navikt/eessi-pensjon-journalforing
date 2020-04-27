![](https://github.com/navikt/eessi-pensjon-journalforing/workflows/Bygg%20og%20deploy%20Q2/badge.svg)
![](https://github.com/navikt/eessi-pensjon-journalforing/workflows/Manuell%20deploy/badge.svg)

# eessi-pensjon-journalforing
Journalfører utgående SED når kafka meldinger konsumeres

# Utvikling

Kjør med java 1.8

## Komme i gang

Dette prosjektet bygger med avhengigheter som ligger i Github Package Registry.
Du må opprette et Personal Access Token (PAT) og enten legge det i
`~/.gradle/gradle.properties`:
```properties
gpr.key=<ditt-token-her>
```
eller sette miljøvariabelen `GITHUB_TOKEN` til verdien av tokenet ditt.

Deretter kan du bygge med:
```
./gradlew build
```

## Oppdatere avhengigheter

Sjekke om man har utdaterte avhengigheter (forsøker å unngå milestones og beta-versjoner):

```
./gradlew dependencyUpdates
```

Dersom du er supertrygg på testene kan du forsøke en oppdatering av alle avhengighetene:

```
./gradlew useLatestVersions && ./gradlew useLatestVersionsCheck
```

## OWASP avhengighetssjekk

```
./gradlew dependencyCheckAnalyze || open build/reports/dependency-check-report.html
```
