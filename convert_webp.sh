#!/bin/bash
DIR="app/src/main/res/drawable-nodpi"

echo "Converting Official Icons to WebP Lossless..."
for COLOR in multicolor red green blue orange gold; do
    SRC="$DIR/icon_$COLOR.png"
    DEST="$DIR/icon_$COLOR.webp"
    if [ -f "$SRC" ]; then
        # Use cwebp if available, otherwise imagemagick
        if command -v cwebp &> /dev/null; then
            cwebp -lossless "$SRC" -o "$DEST"
        else
            convert "$SRC" -quality 100 -define webp:lossless=true "$DEST"
        fi
        rm "$SRC"
    else
        echo "Missing $SRC"
    fi
done

echo "Converting Preview Icons to WebP Lossless..."
for COLOR in multicolor red green blue orange gold; do
    SRC="$DIR/preview_icon_$COLOR.png"
    DEST="$DIR/preview_icon_$COLOR.webp"
    if [ -f "$SRC" ]; then
        if command -v cwebp &> /dev/null; then
            cwebp -lossless "$SRC" -o "$DEST"
        else
            convert "$SRC" -quality 100 -define webp:lossless=true "$DEST"
        fi
        rm "$SRC"
    else
         echo "Missing $SRC"
    fi
done

echo "Verification:"
ls -l "$DIR"/*.webp
identify "$DIR"/*.webp

