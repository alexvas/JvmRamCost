#!/bin/bash
set -euo pipefail

# Скрипт для исправления имён иконок в deb-пакетах
# Использование: ./scripts/fix-deb-icons.sh <путь_к_deb> <productName>
#
# Пример: ./scripts/fix-deb-icons.sh app.deb "jvm-ram-cost-standalone"
#
# Скрипт:
# 1. Распаковывает deb-пакет во временную директорию
# 2. Переименовывает иконки jvm-ram-cost.png в {productName}.png
# 3. Исправляет поле Icon= в .desktop файле
# 4. Пересобирает deb-пакет (перезаписывает исходный файл)

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

# Исправляем .desktop файл
if [ -d "$TEMP_DIR/extracted/usr/share/applications" ]; then
  desktop_files=("$TEMP_DIR/extracted/usr/share/applications"/*.desktop)
  if [ -e "${desktop_files[0]}" ]; then
    for desktop_file in "${desktop_files[@]}"; do
      if grep -q "Icon=jvm-ram-cost" "$desktop_file"; then
        sed -i "s/Icon=jvm-ram-cost/Icon=$PRODUCT_NAME/" "$desktop_file"
        echo "Исправлен .desktop файл: $desktop_file"
      fi
    done
  fi
else
  echo "Предупреждение: директория usr/share/applications не найдена в deb-пакете"
fi

# Пересобираем deb (перезаписываем исходный файл)
dpkg-deb -b "$TEMP_DIR/extracted" "$DEB_PATH"

echo "Готово: $DEB_PATH обновлён с иконками для $PRODUCT_NAME"
