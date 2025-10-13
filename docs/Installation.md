# Installation

The Signer Cloud is packaged as a single Docker image that you can easily deploy in your local environment or any cloud provider, such as Azure by Microsoft or Amazon's AWS.

<!-- begin box warning -->
**Prepare The Database**<br/>
If you have not already prepared the local database, please follow the steps in the [Database Setup](./Database-Setup.md) chapter.
<!-- end -->

## Pull the Docker Image

```sh
docker pull powerauth/signer-cloud-server:${VERSION}
```

## Configure the Docker Image

After you pull the Docker image in your own container repository, you need to prepare the `env.list` file with all the environment variables that are required for the container launch. You can use [Configuration Properties](./Configuration-Properties.md) to bootstrap the configuration.

<!-- begin box warning -->
**Set The Right Database URL**<br/>
The datasource URL for our Docker container follows the structure of the JDBC connectivity. Make sure to provide a valid JDBC URL to the configuration (starting with `jdbc:` prefix). Be especially careful when working on `localhost`! From the Docker container perspective, `localhost` is in the internal network. To connect to your host's localhost, use `host.docker.internal` host name.
<!-- end -->

You need to edit at least the properties below.

```
SIGNER_CLOUD_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/signercloud
SIGNER_CLOUD_DATASOURCE_USERNAME=$USERNAME$
SIGNER_CLOUD_DATASOURCE_PASSWORD=$PASSWORD$
SIGNER_CLOUD_POWERAUTH_URL
SIGNER_CLOUD_EJBCA_URL
SIGNER_CLOUD_EJBCA_KEYSTORE_BASE64
SIGNER_CLOUD_EJBCA_KEYSTORE_PASSWORD
SIGNER_CLOUD_EJBCA_KEY_ALIAS
SIGNER_CLOUD_EJBCA_KEY_PASSWORD
SIGNER_CLOUD_OAUTH2_AUDIENCES
SIGNER_CLOUD_OAUTH2_ISSUER_URI
```

If the database connectivity is set up correctly, the applications will automatically create the right database schema thanks to [Liquibase](https://www.liquibase.org/).

## Start the Docker Container

After you prepare the configuration file, you can run the image using `docker run`:

```sh
docker run --env-file env.list -d -it -p 8080:8000 \
    --name=signer-cloud  powerauth/signer-cloud-server:${VERSION}
```

This will launch the Docker container with the properties you specified and create database schema on the way.

<!-- begin box info -->
The Docker containers use the standard UTC timezone.
<!-- end -->

## Read Next

- [Configuration](./Configuration.md)