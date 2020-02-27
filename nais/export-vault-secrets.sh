#!/usr/bin/env bash

echo "Sjekker eessi-pensjon-journalforing srvPassord"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-journalforing/password;
then
  echo "Setter eessi-pensjon-journalforing srvPassord"
    export srvpassword=$(cat /var/run/secrets/nais.io/srveessi-pensjon-journalforing/password)
fi

echo "Sjekker eessi-pensjon-journalforing srvUsername"
if test -f /var/run/secrets/nais.io/srveessi-pensjon-journalforing/username;
then
    echo "Setter eessi-pensjon-journalforing srvUsername"
    export srvusername=$(cat /var/run/secrets/nais.io/srveessi-pensjon-journalforing/username)
fi

echo "Sjekker APPDYNAMICS_AGENT_ACCOUNT_ACCESS_KEY"
if test -f /var/run/secrets/nais.io/appdynamics/APPDYNAMICS_AGENT_ACCOUNT_ACCESS_KEY;
then
    echo "Setter APPDYNAMICS_AGENT_ACCOUNT_ACCESS_KEY"
    export APPDYNAMICS_AGENT_ACCOUNT_ACCESS_KEY=$(cat /var/run/secrets/nais.io/appdynamics/APPDYNAMICS_AGENT_ACCOUNT_ACCESS_KEY)
fi