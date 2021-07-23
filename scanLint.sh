docker run --rm \
    --network=host \
    -e SONAR_HOST_URL="http://127.0.0.1:9000/"  \
    -e SONAR_LOGIN="7ed77d2942f75687d4cfef1a05569d89839e09be" \
    -v $(pwd)/$foldername:/usr/src/ \
    sonarsource/sonar-scanner-cli \
  -Dsonar.projectKey=Akka-Streams \
  -Dsonar.sonar.projectName=Akka-Streams \
  -Dsonar.sonar.projectVersion=1.0 \
  -Dsonar.sonar.sourceEncoding=UTF-8 \
  -Dsonar.sonar.host.url=http://127.0.0.1:9000/ \
  -Dsonar.login=7ed77d2942f75687d4cfef1a05569d89839e09be