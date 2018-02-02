/*
 * Copyright 2017 California Institute of Technology ("Caltech").
 * U.S. Government sponsorship acknowledged.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * License Terms
 */

package gov.nasa.jpl.imce.xml.catalog.scope

import java.io.{File, IOException}
import java.net.URL

import ammonite.ops.{Path, ls}
import org.apache.xml.resolver.{Catalog, CatalogEntry}

import scala.collection.immutable.{Map, SortedMap, Seq, Set}
import scala.collection.JavaConverters.{asInstanceOf, asScalaBufferConverter}
import scala.{Boolean, None, Option, Some, StringContext, Unit}
import scala.Predef.{augmentString,intWrapper,require,ArrowAssoc,String}
import scala.util.control.Exception.nonFatalCatch

/**
  * CatalogScope is an enhancement of Catalog with the following capabilities:
  * - parseCatalog(URL) tracks successfully parsed catalog or reliably throws IOException.
  * - getParsedCatalogs() reports successfully parsed catalogs
  * - entries() provides access to the catalog entries read across all catalogs successfully parsed.
  * - localFilesScope(predicate) based on the catalog rewrite rules, finds all resolvable files that match the predicate.
  *
  * @see https://xerces.apache.org/xml-commons/components/apidocs/resolver/org/apache/xml/resolver/Catalog.html
  */
class CatalogScope() extends Catalog {

  protected val parsedCatalogs = new scala.collection.mutable.HashSet[URL]()

  private var parsingCatalog: Option[URL] = None

  /**
    * Has this catalog been successfully parsed?
    *
    * @param url catalog
    * @return whether this catalog url has been successfully parsed already.
    */
  def hasParsedCatalog(url: URL): Boolean = parsedCatalogs.contains(url)

  /**
    * Get the successfully parsed catalogs.
    */
  def getParsedCatalogs(): Set[URL] = parsedCatalogs.to[Set]

  /**
    * Keeps track of successfully parsed catalog files.
    *
    * @param url The URL for an absolute path to a catalog file.
    * @throws IOException if the underlying library silently fails to parse a catalog.
    */
  override def parseCatalog(url: URL): Unit
  = synchronized[Unit] {
    require("file" == url.getProtocol)
    val file = new File(url.toURI)
    require(file.isAbsolute)
    if (!hasParsedCatalog(url)) {
      parsingCatalog = Some(url)
      super.parseCatalog(url)
      if (parsingCatalog.nonEmpty) {
        parsingCatalog = None
        throw new IOException(s"parseCatalog($url) failed silently")
      }
    }
  }

  /**
    * Unfortunately, there are circumstances where parsing a catalog fails silently
    * without an exception thrown. One way to detect this condition is to check
    * whether parsePendingCatalogs() has been called during the execution of parseCatalog(URL).
    */
  override protected def parsePendingCatalogs(): Unit = {
    require(parsingCatalog.nonEmpty)
    super.parsePendingCatalogs()
    parsedCatalogs ++= parsingCatalog
    parsingCatalog = None
  }

  /**
    * Get the catalog entries read from the successfully parsed catalogs.
    * @return catalog entries
    *
    * @see https://xerces.apache.org/xml-commons/components/apidocs/resolver/org/apache/xml/resolver/CatalogEntry.html
    */
  def entries(): Seq[CatalogEntry]
  = catalogEntries.asInstanceOf[java.util.Vector[CatalogEntry]].asScala.to[Seq]

  def localFileScope(predicate: CatalogEntryFilePredicate): Map[String, Seq[Path]]
  = {
    implicit val catalogRewritePriority: Ordering[Path]
    = (x: Path, y: Path) => x.toString.length.compare(y.toString.length)

    val rewritePaths: SortedMap[Path, String] = entries().foldLeft(SortedMap.empty[Path, String]) {
      case (acc, entry: CatalogEntry) if Catalog.REWRITE_URI == entry.getEntryType =>

        val rewriteEntry
        : Option[(Path, String)]
        = nonFatalCatch[Option[(Path, String)]]
          .withApply { _ => None }
          .apply {
            val uriStartString = entry.getEntryArg(0)
            val rewritePrefix = new File(entry.getEntryArg(1).stripPrefix("file:"))
            if (rewritePrefix.exists() || rewritePrefix.getParentFile.exists()) {
              val pathPrefix = Path(rewritePrefix)
              if (predicate.apply(uriStartString, pathPrefix))
                Some(pathPrefix -> uriStartString)
              else
                None
            } else
              None
          }

        rewriteEntry.fold(acc) { case (rewritePath, prefix) =>

          if (acc.contains(rewritePath))
            acc
          else
            acc + (rewritePath -> prefix)
        }

      case (acc, _) =>
        acc
    }

    rewritePaths.foldLeft(Map.empty[String, Seq[Path]]) { case (acc, (pathPrefix, uriStartString)) =>
      val localFiles: Set[Path]
      = if (pathPrefix.isDir)
        ls
          .rec((p: Path) => p.isFile && !predicate(uriStartString, p))(pathPrefix)
          .filter(f => predicate(uriStartString, f))
          .to[Set]
      else
        predicate.expandLocalFilePath(pathPrefix)

      acc + (uriStartString -> localFiles.to[Seq].sortBy(_.toString))

    }
  }
}