#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/textmate"
SORA="https://raw.githubusercontent.com/Rosemoe/sora-editor/master/app/src/main/assets/textmate"
VSCODE="https://raw.githubusercontent.com/microsoft/vscode/main/extensions"
SHIKI="https://raw.githubusercontent.com/shikijs/textmate-grammars-themes/main/packages/tm-grammars/grammars"

fetch() {
  mkdir -p "$(dirname "$2")"
  if curl -fsSL -m 60 "$1" -o "$2"; then echo "ok   ${2#"$DEST"/}"; else echo "SKIP $1"; rm -f "$2"; fi
}

# Proven sora sample assets
fetch "$SORA/html/syntaxes/html.tmLanguage.json"            "$DEST/html/syntaxes/html.tmLanguage.json"
fetch "$SORA/html/language-configuration.json"              "$DEST/html/language-configuration.json"
fetch "$SORA/xml/syntaxes/xml.tmLanguage.json"              "$DEST/xml/syntaxes/xml.tmLanguage.json"
fetch "$SORA/xml/language-configuration.json"               "$DEST/xml/language-configuration.json"
fetch "$SORA/javascript/syntaxes/JavaScript.tmLanguage.json" "$DEST/javascript/syntaxes/JavaScript.tmLanguage.json"
fetch "$SORA/javascript/language-configuration.json"        "$DEST/javascript/language-configuration.json"
fetch "$SORA/markdown/syntaxes/markdown.tmLanguage.json"    "$DEST/markdown/syntaxes/markdown.tmLanguage.json"
fetch "$SORA/markdown/language-configuration.json"          "$DEST/markdown/language-configuration.json"
fetch "$SORA/python/syntaxes/python.tmLanguage.json"        "$DEST/python/syntaxes/python.tmLanguage.json"
fetch "$SORA/python/language-configuration.json"            "$DEST/python/language-configuration.json"

# Themes
fetch "$SORA/darcula.json"        "$DEST/themes/darcula.json"
fetch "$SORA/quietlight.json"     "$DEST/themes/quietlight.json"

# VS Code built-in extensions
fetch "$VSCODE/json/syntaxes/JSON.tmLanguage.json"          "$DEST/json/syntaxes/json.tmLanguage.json"
fetch "$VSCODE/json/language-configuration.json"            "$DEST/json/language-configuration.json"
fetch "$VSCODE/yaml/syntaxes/yaml.tmLanguage.json"          "$DEST/yaml/syntaxes/yaml.tmLanguage.json"
fetch "$VSCODE/yaml/language-configuration.json"            "$DEST/yaml/language-configuration.json"
fetch "$VSCODE/ini/syntaxes/ini.tmLanguage.json"            "$DEST/ini/syntaxes/ini.tmLanguage.json"
fetch "$VSCODE/ini/language-configuration.json"             "$DEST/ini/language-configuration.json"
fetch "$VSCODE/shellscript/syntaxes/shell-unix-bash.tmLanguage.json" "$DEST/shell/syntaxes/shell.tmLanguage.json"
fetch "$VSCODE/shellscript/language-configuration.json"     "$DEST/shell/language-configuration.json"
fetch "$VSCODE/typescript-basics/syntaxes/TypeScript.tmLanguage.json" "$DEST/typescript/syntaxes/TypeScript.tmLanguage.json"
fetch "$VSCODE/typescript-basics/language-configuration.json" "$DEST/typescript/language-configuration.json"

# Shiki grammars for TOML, SQL, PowerShell (configs are written separately)
fetch "$SHIKI/toml.json"       "$DEST/toml/syntaxes/toml.tmLanguage.json"
fetch "$SHIKI/sql.json"        "$DEST/sql/syntaxes/sql.tmLanguage.json"
fetch "$SHIKI/powershell.json" "$DEST/powershell/syntaxes/PowerShell.tmLanguage.json"
