# Test Data Loader

## Getting started

### Build the application

Please make sure that a PostgreSQL database in running and that `EHR` schema is initialized.

Adjust the properties define in pom.xml according to your configuration.

```xml
<properties>
    <db.url>jdbc:postgresql://localhost:5432/ehrbase</db.url>
    <db.username>ehrbase</db.username>
    <db.password>ehrbase</db.password>
    <db.driver>org.postgresql.Driver</db.driver>
</properties>
```

Run Maven goals:

```shell
$ mvn clean package
```

### Run the application

```shell
$ java -jar ./target/loader-1.0.0-SNAPSHOT.jar [options]
```

#### Options:

| Name                           | Description                          | Default Value                              |
|--------------------------------|--------------------------------------|--------------------------------------------|
| `--loader.ehr`                 | Number of EHRs to insert.            | `100`                                      |
| `--loader.composition-per-ehr` | Number of compositions for each EHR. | `200`                                      |
| `--spring.datasource.url`      | JDBC URL of the database.            | `jdbc:postgresql://localhost:5432/ehrbase` |
| `--spring.datasource.username` | Login username of the database.      | `ehrbase`                                  |
| `--spring.datasource.password` | Login password of the database.      | `ehrbase`                                  |
