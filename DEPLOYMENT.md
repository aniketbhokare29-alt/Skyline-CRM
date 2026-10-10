# Skyline CRM Deployment

## Render and TiDB Cloud

1. Create a TiDB Cloud Starter database and run `database/mysql_schema.sql`, followed by `database/feature_completion_migration.sql` and `database/sample_data.sql` if sample records are required.
2. In TiDB Cloud, copy the JDBC connection details and create these Render environment variables:
   - `CRM_DB_URL`: the TiDB JDBC URL, including `useSSL=true` and the required timezone options.
   - `CRM_DB_USER`: the TiDB username.
   - `CRM_DB_PASSWORD`: the TiDB password.
3. Connect the GitHub repository to Render and use `render.yaml`, or create a Docker Web Service manually.
4. Render will build the root `Dockerfile`. The service listens on Render's `PORT` and serves both the frontend and `/api` from the same public URL.
5. Open the generated Render URL. The health check is `/api/health`.

## Uploaded documents

The application writes files to `CRM_UPLOAD_DIR`, defaulting to `uploads`. For a real deployment, use a persistent volume or object storage. Render Free Web Services have an ephemeral filesystem, so uploaded documents can be lost after a restart or redeploy.

## Local development

The existing PowerShell scripts continue to run the frontend on port `5500` and the API on port `8080`. In that mode, the frontend automatically uses the separate local API URL.
