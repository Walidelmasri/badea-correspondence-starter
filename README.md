# BADEA Correspondence

Frontend for the Presidency correspondence workspace.

## Development workflow

1. Develop and test locally.
2. Commit source code to Git.
3. Push the approved repository to GitHub/private source control.
4. Build the production Angular bundle.
5. Deploy the built frontend as a container in the existing Alfresco Docker Compose stack.

The Linux server is a deployment target, not the primary development workspace.

## Requirements

Angular 22 requires a supported Node.js release. Check the current Angular compatibility table before setting up a new machine.

## Run locally

```bash
npm install
npm start
```

Then open:

```text
http://localhost:4200
```

## Production build for Alfresco sub-path

```bash
npm run build:correspondence
```

The resulting application is built with:

```text
/correspondence/
```

as its base path, ready for a reverse-proxy route such as:

```text
https://alfresco.internal.badea.org/correspondence/
```

## Git

Initialize locally:

```bash
git init
git add .
git commit -m "chore: initialize correspondence frontend"
```

Create a **private** repository for the real BADEA implementation unless BADEA explicitly approves public release.

Do not commit credentials, internal certificates, private deployment configuration, production documents, employee data, or authentication secrets.

## Arabic / Tajawal

The application is Arabic-first and starts with RTL direction.

Tajawal should be self-hosted in the production image using BADEA-approved font assets. Font binaries are intentionally not included in this repository starter.

## Architecture

This starter intentionally contains only the application foundation. Alfresco integration, authentication, correspondence domain logic, and PDF tooling will be added feature-by-feature.
