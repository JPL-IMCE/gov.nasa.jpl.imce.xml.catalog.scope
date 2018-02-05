import sbt._

name := "gov.nasa.jpl.imce.xml.catalog.scope"

moduleName := name.value

description := "Enhanced Apache XML Commons Resolver library with reverse catalog scope functionality."

organization := "gov.nasa.jpl.imce"

organizationName := "JPL-IMCE"

startYear := Some(2017)

headerLicense := Some(HeaderLicense.Custom(
  """|Copyright 2017 California Institute of Technology ("Caltech").
     |U.S. Government sponsorship acknowledged.
     |
     |Licensed under the Apache License, Version 2.0 (the "License");
     |you may not use this file except in compliance with the License.
     |You may obtain a copy of the License at
     |
     |    http://www.apache.org/licenses/LICENSE-2.0
     |
     |Unless required by applicable law or agreed to in writing, software
     |distributed under the License is distributed on an "AS IS" BASIS,
     |WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     |See the License for the specific language governing permissions and
     |limitations under the License.
     |License Terms
     |""".stripMargin
))

scalaVersion := Versions.scala212

crossScalaVersions := Seq(Versions.scala212, Versions.scala211)

// https://mvnrepository.com/artifact/xml-resolver/xml-resolver
libraryDependencies += "xml-resolver" % "xml-resolver" % "1.2"

libraryDependencies += "com.lihaoyi" %% "ammonite" % Versions.ammonite cross CrossVersion.full

resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"
scalacOptions in (Compile, compile) += s"-P:artima-supersafe:config-file:${baseDirectory.value}/project/supersafe.cfg"
scalacOptions in (Test, compile) += s"-P:artima-supersafe:config-file:${baseDirectory.value}/project/supersafe.cfg"
scalacOptions in (Compile,doc) ++= Seq("-groups", "-implicits", "-Xplugin-disable:artima-supersafe")
scalacOptions in (Test, doc) ++= Seq("-groups", "-implicits", "-Xplugin-disable:artima-supersafe")

scalacOptions in (Compile,doc) ++= Seq(
  "-diagrams",
  "-doc-title", name.value,
  "-doc-root-content", baseDirectory.value + "/rootdoc.txt")

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

autoAPIMappings := true

apiURL := Some(url("https://jpl-imce.github.io/gov.nasa.jpl.imce.xml.catalog.scope/latest/api/"))

scmInfo := Some(ScmInfo(
  browseUrl = url(s"https://github.com/${organizationName.value}/${moduleName.value}"),
  connection = "scm:git@github.com:JPL-IMCE/gov.nasa.jpl.imce.xml.catalog.scope.git"))

developers := List(
  Developer(
    id="NicolasRouquette",
    name="Nicolas F. Rouquette",
    email="nicolas.f.rouquette@jpl.nasa.gov",
    url=url("https://github.com/NicolasRouquette")))

