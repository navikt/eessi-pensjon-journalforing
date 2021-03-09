![](https://github.com/navikt/eessi-pensjon-journalforing/workflows/Bygg%20og%20deploy%20Q2/badge.svg)
![](https://github.com/navikt/eessi-pensjon-journalforing/workflows/Manuell%20deploy/badge.svg)

# eessi-pensjon-journalforing
Journalfører utgående SED når kafka meldinger konsumeres

# Utvikling

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

(Pass på at du kan nå `ossindex.sonatype.org` og `nvd.nist.gov` gjennom evt proxy e.l.) 

```
./gradlew dependencyCheckAnalyze && open build/reports/dependency-check-report.html
```

## SonarQube
Hentet fra [SONARQUBE](https://docs.sonarqube.org/latest/setup/get-started-2-minutes/)


1. Start sonarQube lokalt
```
docker run -d --name sonarqube -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true -p 9000:9000 sonarqube:latest
```
2. Oppsett av prosjekt, inne i sonarqube
```
1. Click the Create new project button.
2. Give your project a Project key and a Display name and click the Set Up button.
3. Under Provide a token, select Generate a token. Give your token a name, click the Generate button, and click Continue.
4. Select your project's main language under Run analysis on your project, and follow the instructions to analyze your project. Here you'll download and execute a Scanner on your code (if you're using Maven or Gradle, the Scanner is automatically downloaded)
```
3. Resultat fra 2 vil da være en kommando lignende den under, som kjøres i terminal: 
````
./gradlew sonarqube \
  -Dsonar.projectKey=eessi-pensjon-journalforing \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=4cb8223815284772475c8f59d898eecaf8020b87
  ````

4. Sjekk resultatat på http://localhost:9000/dashboard?id=eessi-pensjon-journalforing