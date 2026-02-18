#!/bin/bash

if [ $# -ne 2 ]; then
    echo "Usage: $0 <fichier_source> <fichier_destination>"
    exit 1
fi

FICHIER_SRC=$1
FICHIER_DST=$2
BIN_DIR="bin"

echo "--- Téléchargement de $FICHIER_SRC vers $FICHIER_DST ---"
java -cp "$BIN_DIR" main.client.ClientDownload $FICHIER_SRC $FICHIER_DST
