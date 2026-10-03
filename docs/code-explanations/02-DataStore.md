# DataStore.java Explanation

File:

`src/main/java/com/saivandan/crm/DataStore.java`

## Purpose

`DataStore.java` is the database layer. It connects Java to MySQL using JDBC.

## Main responsibilities

- Creates database tables.
- Adds new records.
- Reads records.
- Authenticates registered users.
- Inserts demo data when the database is empty.

## Common method patterns

Add methods:

```text
addEnquiry()
addProperty()
addEmployee()
addVendor()
addCustomerPayment()
```

Read methods:

```text
getEnquiries()
getProperties()
getEmployees()
getVendors()
getCustomerPayments()
```

## Database flow

```text
Form data -> add method -> model record -> SQL INSERT -> MySQL
MySQL -> SQL SELECT -> model record list -> API response
```

The class uses `Connection`, `PreparedStatement`, `Statement`, `ResultSet`, and `DriverManager` from JDBC.
