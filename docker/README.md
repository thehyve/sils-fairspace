# Fairspace deployment with Docker

This folder contains `docker-compose` scripts for running:
- Fairspace, including *Mercury* front-end, *Pluto* proxy, 
  *Saturn* back-end and its databases (`docker-compose.yml`);
- Keycloak and its database (`keycloak.yml`);
- An SSL proxy for Fairspace and Keycloak (`ssl-proxy-docker-compose.yml`).

Fairspace uses Keycloak for authentication. It is preferred to have a Keycloak instance at organisation level,
Keycloak `docker-compose` script can be excluded, and an existing Keycloak instance can be used, 
configured as described in the main Fairspace documentation.
By default, a predefined `fairspace` realm configuration is imported to Keycloak using [realm template](./keycloak/realm-template.json).

Please ensure that you have a recent version of Docker (>= `18`).
If you do not have `docker-compose` installed,
follow the instructions to [install docker-compose](https://docs.docker.com/compose/install/).

## Deployment configuration

Create a `.env` file with the following variables:

Variable                   | Description
:------------------------- |:---------------
`DOCKER_HOST_IP`           | IP address of the Docker host. For standard Linux distributions it should be configured to 172.17.0.1. 
`KEYCLOAK_SERVER_URL`      | URL of the Keycloak server e.g. `https://keycloak.example.com`
`KEYCLOAK_REALM`           | Keycloak realm, e.g. `fairspace`
`KEYCLOAK_CLIENT_ID`       | Keycloak client id, e.g. `fairspace-client`
`KEYCLOAK_CLIENT_SECRET`   | Keycloak client secret, e.g. `**********`
`POSTGRESQL_PORT`          | Fairspace views PostgreSQL database port, e.g. `5432`
`FAIRSPACE_URL`            | URL of Fairspace, e.g. `https://fairspace.example.com`
`PLUTO_LOGLEVEL`           | Level of Pluto application logs, default: `INFO`
`PLUTO_CONNECT_TIMEOUT_MILLIS`| Pluto connection timeout in milliseconds, default: `600000`.
`PLUTO_SOCKET_TIMEOUT_MILLIS`| Pluto socket timeout in milliseconds, default: `2000`.
`PLUTO_IMAGE`              | Path to the docker image of Pluto, e.g. `eu.gcr.io/fairspace-207108/sils-pluto:0.1.2` or `pluto-local:latest` if deploying the local build
`SATURN_IMAGE`             | Path to the docker image of Mercury, e.g. `eu.gcr.io/fairspace-207108/mercury:0.1.2` or `mercury-local:latest` if deploying the local build
`FAIRSPACE_SSL_PROXY_IMAGE`| Path to the docker image of SSL proxy, e.g. `eu.gcr.io/fairspace-207108/fairspace-ssl-proxy:0.1.2` or `fairspace-ssl-proxy-local:latest` if deploying the local build
`KEYCLOAK_HOSTNAME`        | FQDN of the Keycloak server, e.g., `keycloak.example.com`
`FAIRSPACE_HOSTNAME`       | FQDN of the Fairspace server, e.g., `fairspace.example.com`

## Certificates

To enable SSL, Nginx is used as a proxy.

There can be 2 different types of your certificates:
* Self-signed certificates: you can generate your own certificates for development purposes.
  Be aware that in production your self-signed certificates will not be accepted by the users browser.

* Certificates signed by one of CA (certificate authorities).
  That can be commercial ones or free (like Let's encrypt), or your organisation CA.

### Self-signed certificates

**NOTE!**
*Browsers do not trust self-signed certificates by default. Depending on a browser, you may need several steps 
to allow opening Fairspace and Keycloak pages with this type of certificate.*

You can generate a self-signed certificate using OpenSSL (1.1.1 or newer).
To generate a self-signed certificate for hostname `example.com`
with aliases `keycloak.example.com` and `fairspace.example.com`, run, e.g.:
```bash
openssl req -new -newkey rsa:4096 -x509 -sha256 -days 365 -nodes \
  -out ssl/server.pem -keyout ssl/server.key \
  -subj "/C=NL/ST=Utrecht/L=Utrecht/O=The Hyve/CN=example.com" \
  -addext "subjectAltName=DNS:keycloak.example.com,DNS:fairspace.example.com"
```

To generate a self-signed certificate for localhost:
```bash
openssl req -new -newkey rsa:4096 -x509 -sha256 -days 365 -nodes \
-out ssl/server.pem -keyout ssl/server.key \
-subj "/C=NL/ST=Utrecht/L=Utrecht/O=The Hyve/CN=localhost" \
-addext "subjectAltName=DNS:fairspace-keycloak,DNS:fairspace"
```

### Certificates signed by CA

CA usually provide you with 4 files of following types:

* certificate file for your hostname (eg. `cert.pem`);

* private key file for your hostname (eg. `privkey.pem`);

* chain file (eg. `chain.pem`) - this file contains a certificate chain of CA

    ```bash
    -----BEGIN CERTIFICATE-----
      Certificate of Root CA
    -----END CERTIFICATE-----
    -----BEGIN CERTIFICATE-----
      Certificate of CA(1) signed by Root CA
    -----END CERTIFICATE-----
    ...
    -----BEGIN CERTIFICATE-----
      Certificate of CA(n) signed by CA(n-1)
    -----END CERTIFICATE-----
    ```

  in the simplest scenario that may contain just 1 certificate;

* full-chain file (eg. `fullchain.pem`) - this file is a concatenation of `cert.pem` and `chain.pem` files;

Sometimes you don't have a full-chain file, but that is not a problem, since it is possible to create one by yourself:

```bash
cp cert.pem fullchain.pem
cat chain.pem >> fullchain.pem
```

From those 4 files this solution requires `cert.pem` and `full-chain.pem` files.
Copy them to `ssl` directory:

```bash
cp privkey.pem ssl/server.key
cp fullchain.pem ssl/server.pem
```

### Common tasks

You should also copy the file `ssl/server.pem` to
`ssl/extra_certs.pem` to have the certificate accepted by the services.
This is for instance needed for the backend services to verify
an access token with Keycloak.

`cp ssl/server.pem ssl/extra_certs.pem`

## Keycloak

Keycloak is configured for use with Fairspace at first startup, using the
[realm configuration template](keycloak/realm-template.json). To disable this,
comment out the `KEYCLOAK_IMPORT` line in [keycloak.yml](keycloak.yml) and configure the Keycloak realm manually,
following the instructions from [Fairspace documentation](../README.adoc) on how to configure a Keycloak realm for Fairspace.

Importing the realm template results in:
- creating a `fairspace` realm,
- creating a default client `fairspace-client` with secret: `"**********"` that should be regenerated after starting Keycloak,
- adding a default user that has a `superadmin` role with username: `"organisation-admin"` and password: `"keycloak123"`
  (to be changed in Keycloak Administration Console after starting Keycloak).

In order to regenerate the `fairspace-client` secret:
1. Open the Keycloak Administration Console (default username: `keycloak`, default password: `keycloak`),
2. Open `fairspace` realm settings,
3. Go to Clients and select `fairspace-client`,
4. Open "Credentials" tab and clisk on "Regenerate Secret" button,
5. Copy the newly generated secret into the `.env` file and restart fairspace-saturn and fairspace-pluto containers.

## Running everything together 

After configuring the `.env` file and certificates, the `docker-compose` script can be run.
Before running the script, make sure that the ports configured to be exposed are not occupied 
(default ones are `:80`, `:443`, `8080` and `:9080`). 

To start a component use the following command:
```bash
docker-compose -f <config-file.yml> up
```
In addition, you can use the `-d` flag to run containers in the detached mode (run containers in the background).
```bash
docker-compose -f <config-file.yml> up -d
```

To run Fairspace, Keycloak and SSL proxy together, use the following command:
```bash
docker-compose -f docker-compose.yml -f keycloak-docker-compose.yml -f ssl-proxy-docker-compose.yml up -d
```
or use the script:
```bash
bash start_all.sh
```

To stop all the components, use:
```bash
docker-compose -f docker-compose.yml -f keycloak-docker-compose.yml -f ssl-proxy-docker-compose.yml down
```
or use the script:
```bash
bash start_all.sh
```

## Building and running images locally

If you want to try these scripts locally, without having separate DNS records
for Fairspace and Keycloak pointing to your machine, some additional steps are
required:

1. Add hostnames to your `etc/hosts` file:
    ```
    127.0.0.1       keycloak
    127.0.0.1       fairspace
    ```
2. Add `extra_hosts` to the `fairspace-pluto` and `fairspace-saturn` services in `docker-compose.yml`:
    ```yaml
    extra_hosts:
      - "keycloak:172.17.0.1"
    ```
   Where the `172.17.0.1` is the IP address of the gateway between the Docker host and the bridge network default for Docker on Linux. 
   If using a different Operating System, the IP address has to be changed accordingly.

3. Set these local aliases as host names in the `.env` file:
    ```properties
    KEYCLOAK_SERVER_URL=https://keycloak
    FAIRSPACE_HOSTNAME=fairspace
    KEYCLOAK_HOSTNAME=keycloak
    ```
4. Use `localhost`, `keycloak` and `fairspace` when generating the certificate

5. To build the images locally from the source code, instead of using existing images, use `deploy.sh` script. 
   It will create 3 local images: `fairspace-ssl-proxy-local:latest`, `pluto-local:latest` and `saturn-local:latest`.
   Set these local tags as image names in the `.env` file:
    ```properties
    PLUTO_IMAGE=pluto-local:latest
    SATURN_IMAGE=saturn-local:latest
    FAIRSPACE_SSL_PROXY_IMAGE=fairspace-ssl-proxy-local:latest
    ```
   The script will build the images and run all the components. Run the script with:
    ```bash
    bash deploy.sh
    ```
   If you want to use existing images instead, configure the image names in `.env` file and run:
    ```bash
    bash start_all.sh
    ```

To stop all containers run:
```bash
bash stop_all.sh
```

## Logs

Logs are written to `journald` by default. The logs can be inspected with
```bash
journalctl -f -u docker.service
```
and for individual services with `docker logs <service-name> -f`, e.g.,
```bash
docker logs fairspace-saturn -f
```

If `journald` is not available (e.g., on MacOS),
add `DOCKER_LOGGING_DRIVER=json-file` to the `.env` file.
Logs can then still be inspected with `docker logs`, but not with `journalctl`.
