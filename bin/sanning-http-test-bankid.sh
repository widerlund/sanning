#!/bin/bash

# Usage.
if (($# != 1))
then
  echo "sanning-http-test-bankid.sh <port>"
  exit 2
fi

# Start sanning-http.sh with BankID authenticator and https.
./sanning-http.sh $1 https://appapi2.test.bankid.com/rp/v5.1 lib/sanning-test-bankid.jks sanning
