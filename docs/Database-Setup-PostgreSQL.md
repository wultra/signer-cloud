# Database Setup

Signer Cloud uses PostgreSQL database as the primary datastore type. While it is possible to configure the images to use other database engine as well (Oracle, MySQL, etc.), we recommend PostgreSQL as the stable, performant and open choice.

<!-- begin box info -->
The Docker images automatically keep the database schema up-to-date using [Liquibase](https://www.liquibase.org/). Whenever you install the application or update it to a new version, the container updates the DB schema. On application downgrade, the changes are rolled back automatically (losing information specific for the new versions). **Therefore, you only need to follow this documentation when you are installing and setting up the database from scratch**.
<!-- end -->

## Install PostgreSQL

To get started, download and install the current version of PostgreSQL:

- [PostgreSQL Downloads](https://www.postgresql.org/download/)

Start the database and connect to it using your preferred database tool.

<!-- begin box success -->
**Tip:** We use [DataGrip by JetBrains](https://www.jetbrains.com/datagrip/) for any database related tasks. It allows easy data editing for all popular database engines and contains a powerful SQL console for even the most complex database tasks.
<!-- end -->

## Create User

Start by creating the `signercloud` user in the database and by setting the user a strong password.

<!-- begin box info -->
**Tip:** You can generate a strong password locally on your computer using `openssl rand -base64 12`.
<!-- end -->

```sql
CREATE USER signercloud;
ALTER USER signercloud WITH PASSWORD '$PASSWORD$';
```

## Create Database

Now, let's create the `signercloud` database to which we will store the data.

```sql
CREATE DATABASE signercloud;
GRANT ALL PRIVILEGES ON DATABASE signercloud TO signercloud;
```

You can assign more granular privileges instead of using `ALL PRIVILEGES`, if required for security reasons.

## Read Next

This is everything you need at this moment. Once the database is up and running with the right user and database, you can launch the Docker container. The Docker container uses [Liquibase](https://www.liquibase.org/) to create the schema automatically.

- [Installation](./Installation.md)