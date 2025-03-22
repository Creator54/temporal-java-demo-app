#!/bin/bash

echo "Starting SigNoz complete data cleanup..."

# Get the ClickHouse container ID
CLICKHOUSE_CONTAINER=$(docker ps -q --filter "name=signoz-clickhouse")

if [ -z "$CLICKHOUSE_CONTAINER" ]; then
    echo "Error: ClickHouse container not found!"
    exit 1
fi

echo "Found ClickHouse container: $CLICKHOUSE_CONTAINER"

# Clean ALL metrics tables
echo "Truncating ALL metrics tables..."
docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "SHOW TABLES FROM signoz_metrics" | while read table; do
    echo "Truncating signoz_metrics.$table"
    docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "TRUNCATE TABLE signoz_metrics.$table"
done

# Clean ALL traces tables
echo "Truncating ALL traces tables..."
docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "SHOW TABLES FROM signoz_traces" | while read table; do
    echo "Truncating signoz_traces.$table"
    docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "TRUNCATE TABLE signoz_traces.$table"
done

# Clean ALL logs tables
echo "Truncating ALL logs tables..."
docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "SHOW TABLES FROM signoz_logs" | while read table; do
    echo "Truncating signoz_logs.$table"
    docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "TRUNCATE TABLE signoz_logs.$table"
done

# Clean ALL metadata tables
echo "Truncating ALL metadata tables..."
docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "SHOW TABLES FROM signoz_metadata" | while read table; do
    echo "Truncating signoz_metadata.$table"
    docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "TRUNCATE TABLE signoz_metadata.$table"
done

# Clean ALL analytics tables
echo "Truncating ALL analytics tables..."
docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "SHOW TABLES FROM signoz_analytics" | while read table; do
    echo "Truncating signoz_analytics.$table"
    docker exec $CLICKHOUSE_CONTAINER clickhouse-client --query "TRUNCATE TABLE signoz_analytics.$table"
done

# Restart all SigNoz services to ensure clean reload
echo "Restarting SigNoz services..."
docker restart signoz-query-service
docker restart signoz-otel-collector
docker restart signoz-frontend

echo "SigNoz data cleanup completed successfully! You now have a completely clean slate."
echo "Note: You may need to wait a few minutes for all services to restart properly." 