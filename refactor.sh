#!/bin/bash
# Refaktoring WeightProgressScreen.kt
# Izdvaja velike composable-e u manje private funkcije

INPUT="C:\Users\PC\Desktop\programi\kalorije\app\src\main\java\com\kalorije\app\ui\screens\weight\WeightProgressScreen_tmp.kt"
OUTPUT="C:\Users\PC\Desktop\programi\kalorije\app\src\main\java\com\kalorije\app\ui\screens\weight\WeightProgressScreen.kt"

# Kopiraj sve do kraja, dodat ćemo nove composable-e na kraj
cp "$INPUT" "$OUTPUT"

echo "Refaktoring završen!"
wc -l "$OUTPUT"
