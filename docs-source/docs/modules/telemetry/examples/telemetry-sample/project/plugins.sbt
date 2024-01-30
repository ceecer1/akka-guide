// tag::telemetry-sbt-plugin[]
// The Cinnamon Telemetry plugin
addSbtPlugin("com.lightbend.cinnamon" % "sbt-cinnamon" % "2.17.3")
// end::telemetry-sbt-plugin[]

// tag::telemetry-javaagent-docker[]
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.13")
// end::telemetry-javaagent-docker[]

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
