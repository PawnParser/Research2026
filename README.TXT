u22857941 Christopher Yoko Research Project

=============================

Compile:

javac -d out src\avparser\*.java

=============================

Run every deck in lectures\, writing one folder per deck under output\:

Get-ChildItem lectures\*.pptx | ForEach-Object { java -cp out avparser.PptxAccessibilityTool $_.FullName "output\$($_.BaseName)" }

=============================

Run a single deck:

java -cp out avparser.PptxAccessibilityTool lectures\chapter12_part2_red_black.pptx output\redblack

