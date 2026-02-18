#!/bin/bash

if [ $# -ne 1 ]; then
    echo "Usage: $0 <fichier_a_uploader>"
    exit 1
fi

FICHIER=$1
BIN_DIR="bin"

if [ ! -f "$FICHIER" ]; then
    echo "Fichier $FICHIER introuvable."
    exit 1
fi

echo "--- Upload de $FICHIER ---"
java -cp "$BIN_DIR" main.client.Client $FICHIER
