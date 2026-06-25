#!/bin/bash
# ZenoLabs — Backend launcher (macOS)
# Delegates to labs-backend/run_backend.sh.
cd "$(dirname "$0")/labs-backend"
bash run_backend.sh
