#!/bin/bash
set -euo pipefail

# Скрипт для исправления имён артефактов в deb-пакетах
# Использование: ./scripts/fix-deb-icons.sh <путь_к_deb> <productName>
#
# Пример: ./scripts/fix-deb-icons.sh app.deb "jvm-ram-cost-jdk25"
#
# Скрипт:
# 1. Распаковывает deb-пакет во временную директорию
# 2. Переименовывает бинарник /usr/bin/jvm-ram-cost в /usr/bin/{productName}
# 3. Переименовывает иконки jvm-ram-cost.png в {productName}.png
# 4. Исправляет поля Icon= и Exec= в .desktop файле
# 5. Пересобирает deb-пакет (перезаписывает исходный файл)

if [ $# -ne 2 ]; then
  echo "Использование: $0 <путь_к_deb> <productName>"
  exit 1
fi

DEB_PATH="$1"
PRODUCT_NAME="$2"

if [ ! -f "$DEB_PATH" ]; then
  echo "Ошибка: файл $DEB_PATH не найден"
  exit 1
fi

# Создаём временную директорию
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# Распаковываем deb
dpkg-deb -R "$DEB_PATH" "$TEMP_DIR/extracted"

# Переименовываем бинарник /usr/bin/jvm-ram-cost в /usr/bin/{productName}
OLD_BIN="$TEMP_DIR/extracted/usr/bin/jvm-ram-cost"
NEW_BIN="$TEMP_DIR/extracted/usr/bin/$PRODUCT_NAME"
if [ -f "$OLD_BIN" ]; then
  mv "$OLD_BIN" "$NEW_BIN"
  echo "Переименован бинарник: $OLD_BIN -> $NEW_BIN"
else
  echo "Предупреждение: бинарник /usr/bin/jvm-ram-cost не найден в deb-пакете"
fi

# Переименовываем иконки jvm-ram-cost.png в {productName}.png
if [ -d "$TEMP_DIR/extracted/usr/share/icons" ]; then
  find "$TEMP_DIR/extracted/usr/share/icons" -name "jvm-ram-cost.png" | while read -r icon_path; do
    icon_dir=$(dirname "$icon_path")
    new_icon_path="$icon_dir/$PRODUCT_NAME.png"
    mv "$icon_path" "$new_icon_path"
    echo "Переименована иконка: $icon_path -> $new_icon_path"
  done
else
  echo "Предупреждение: директория usr/share/icons не найдена в deb-пакете"
fi

# Исправляем .desktop файл (Icon= и Exec=)
if [ -d "$TEMP_DIR/extracted/usr/share/applications" ]; then
  desktop_files=("$TEMP_DIR/extracted/usr/share/applications"/*.desktop)
  if [ -e "${desktop_files[0]}" ]; then
    for desktop_file in "${desktop_files[@]}"; do
      modified=false
      if grep -q "Icon=jvm-ram-cost" "$desktop_file"; then
        sed -i "s/Icon=jvm-ram-cost/Icon=$PRODUCT_NAME/" "$desktop_file"
        modified=true
      fi
      if grep -q "Exec=jvm-ram-cost" "$desktop_file"; then
        sed -i "s|Exec=jvm-ram-cost|Exec=$PRODUCT_NAME|" "$desktop_file"
        modified=true
      fi
      if [ "$modified" = true ]; then
        echo "Исправлен .desktop файл: $desktop_file"
      fi
    done
  fi
else
  echo "Предупреждение: директория usr/share/applications не найдена в deb-пакете"
fi

# Пересобираем deb (перезаписываем исходный файл)
dpkg-deb -b "$TEMP_DIR/extracted" "$DEB_PATH"

echo "Готово: $DEB_PATH обновлён для $PRODUCT_NAME"
