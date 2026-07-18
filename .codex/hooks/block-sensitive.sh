#!/bin/bash
# block-sensitive.sh — 阻止编辑敏感文件
# 从 stdin 读取工具输入 JSON，检查 file_path 是否匹配敏感文件模式
input=$(cat)
file_path=$(echo "$input" | grep -oP '"file_path"\s*:\s*"[^"]+"' | head -1 | grep -oP '"[^"]+"$' | tr -d '"')

# 敏感文件模式
case "$file_path" in
  *.jks|*.keystore)
    echo "BLOCK: 签名密钥文件禁止编辑 ($file_path)"
    exit 1
    ;;
  */local.properties)
    echo "BLOCK: local.properties 包含本地路径配置，禁止编辑 ($file_path)"
    exit 1
    ;;
  */gradle.properties)
    echo "BLOCK: gradle.properties 包含构建配置，请手动编辑 ($file_path)"
    exit 1
    ;;
  *.env|.env*)
    echo "BLOCK: 环境变量文件禁止编辑 ($file_path)"
    exit 1
    ;;
esac

exit 0
