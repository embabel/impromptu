#!/bin/bash
# Load reference data into Neo4j using fast CSV import
#
# Usage: ./scripts/load_data.sh
#
# Environment variables (with defaults):
#   NEO4J_USERNAME=neo4j
#   NEO4J_PASSWORD=brahmsian

set -e

# Get project root (parent of scripts directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
DATA_DIR="$PROJECT_ROOT/data/exports"

# Neo4j connection settings
USER="${NEO4J_USERNAME:-neo4j}"
PASS="${NEO4J_PASSWORD:-brahmsian}"
CONTAINER="impromptu-neo4j"

# Check if Neo4j container is running
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
    echo "Error: Neo4j container '${CONTAINER}' is not running"
    echo "Start it with: docker compose up -d"
    exit 1
fi

# Function to run a Cypher query and get the result
run_query() {
    docker exec "$CONTAINER" cypher-shell -u "$USER" -p "$PASS" "$1" 2>/dev/null
}

# Check if data is already loaded by counting Composers
COMPOSER_COUNT=$(run_query "MATCH (c:Composer) RETURN count(c) AS count" | tail -1 | tr -d ' ')

if [ "$COMPOSER_COUNT" != "0" ] && [ -n "$COMPOSER_COUNT" ]; then
    echo "Data already loaded (found $COMPOSER_COUNT composers). Skipping."
    exit 0
fi

echo "No existing data found. Loading reference data..."
echo ""

# Check for required files
if [ ! -f "$DATA_DIR/composers.csv" ]; then
    echo "Error: CSV files not found in $DATA_DIR"
    echo "Run the export from the app first (Export Cypher button in Influences tab)"
    exit 1
fi

# Copy CSV files to Neo4j import directory
echo "Copying CSV files to Neo4j import directory..."
for csv in composers.csv works.csv epochs.csv genres.csv composed_rels.csv epoch_rels.csv genre_rels.csv; do
    if [ -f "$DATA_DIR/$csv" ]; then
        docker cp "$DATA_DIR/$csv" "$CONTAINER:/var/lib/neo4j/import/"
        echo "  Copied $csv"
    fi
done

# Run the import
echo ""
echo "Importing data (this uses LOAD CSV for fast bulk import)..."
start_time=$(date +%s)

docker exec -i "$CONTAINER" cypher-shell -u "$USER" -p "$PASS" < "$DATA_DIR/import_references.cypher"

end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo "  Reference data imported in ${elapsed}s"

# Load additional reference data (instruments, techniques)
if [ -f "$DATA_DIR/create_reference_data.cypher" ]; then
    echo ""
    echo "Loading additional reference data (instruments, techniques)..."
    start_time=$(date +%s)

    docker exec -i "$CONTAINER" cypher-shell -u "$USER" -p "$PASS" < "$DATA_DIR/create_reference_data.cypher"

    end_time=$(date +%s)
    elapsed=$((end_time - start_time))
    echo "  Additional data imported in ${elapsed}s"
fi

# Show summary
echo ""
echo "Load complete. Summary:"
docker exec "$CONTAINER" cypher-shell -u "$USER" -p "$PASS" --format plain "
CALL { MATCH (c:Composer) RETURN count(c) AS composers }
CALL { MATCH (w:Work) RETURN count(w) AS works }
CALL { MATCH (e:Epoch) RETURN count(e) AS epochs }
CALL { MATCH (g:Genre) RETURN count(g) AS genres }
CALL { MATCH (i:Instrument) RETURN count(i) AS instruments }
CALL { MATCH (t:Technique) RETURN count(t) AS techniques }
CALL { MATCH ()-[r:COMPOSED]->() RETURN count(r) AS composed }
CALL { MATCH ()-[r:OF_EPOCH]->() RETURN count(r) AS ofEpoch }
CALL { MATCH ()-[r:OF_GENRE]->() RETURN count(r) AS ofGenre }
RETURN composers + ' Composers, ' + works + ' Works, ' + epochs + ' Epochs, ' + genres + ' Genres, ' +
       instruments + ' Instruments, ' + techniques + ' Techniques | ' +
       composed + ' COMPOSED, ' + ofEpoch + ' OF_EPOCH, ' + ofGenre + ' OF_GENRE'
" 2>/dev/null | tail -1 | tr -d '"'
