#!/bin/bash

# Usage.
if (($# != 1))
then
  echo "sanning-http-test.sh <port>"
  exit 2
fi

# Start sanning-http.sh without authenticator and plain http.
./sanning-http.sh $1 test
