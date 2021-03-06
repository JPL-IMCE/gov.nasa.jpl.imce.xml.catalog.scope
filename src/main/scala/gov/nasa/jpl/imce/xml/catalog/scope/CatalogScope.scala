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

import ammonite.ops.{Path, ls, up}
import org.apache.xml.resolver.{Catalog, CatalogEntry}

import org.scalactic.Requirements.{requirementsHelper,require,requireState}
import scala.collection.immutable.{Map, SortedMap, Seq, Set}
import scala.collection.JavaConverters.{asInstanceOf, asScalaBufferConverter}
import scala.{Boolean, None, Option, Some, StringContext, Unit}
import scala.Predef.{augmentString,intWrapper,ArrowAssoc,String}
import scala.util.control.Exception.nonFatalCatch

/**
  * CatalogScope is an enhancement of Catalog with the following capabilities:
  * - parseCatalog(URL) tracks successfully parsed catalog or reliably throws IOException.
  * - getParsedCatalogs() reports successfully parsed catalogs
  * - entries() provides access to the catalog entries read across all catalogs successfully parsed.
  * - localFilesScope(predicate) based on the catalog rewrite rules, finds all resolvable files that match the predicate.
  *
  * @see https://xerces.apache.org/xml-commons/components/apidocs/resolver/org/apache/xml/resolver/Catalog.html
  * @group scope
  */
class CatalogScope() extends Catalog {

  protected val parsedCatalogs = new scala.collection.mutable.HashSet[Path]()

  private var isParsing: Boolean = false
  private var parsingCatalog: Option[Path] = None

  /**
    * Has this catalog been successfully parsed?
    *
    * @param catFile catalog
    * @return whether this catalog url has been successfully parsed already
    * @group scope
    */
  def hasParsedCatalog(catFile: Path): Boolean = parsedCatalogs.contains(catFile)

  /**
    * Get the successfully parsed catalogs.
    * @group scope
    */
  def getParsedCatalogs(): Set[Path] = parsedCatalogs.to[Set]

  /**
    * Keeps track of successfully parsed catalog files.
    *
    * @param url The URL for an absolute path to a catalog file.
    * @throws java.io.IOException if the underlying library silently fails to parse a catalog.
    * @group scope
    */
  override def parseCatalog(url: URL): Unit
  = synchronized[Unit] {
    require("file" == url.getProtocol)
    val file = Path(url.toURI.getPath)
    require(file.toIO.isAbsolute)
    if (!hasParsedCatalog(file)) {
      isParsing = true
      parsingCatalog = Some(file)
      super.parseCatalog(url)
      if (parsingCatalog.nonEmpty) {
        isParsing = false
        parsingCatalog = None
        throw new IOException(s"parseCatalog($url) failed silently")
      }
    }
  }

  /**
    * Unfortunately, there are circumstances where parsing a catalog fails silently
    * without an exception thrown. One way to detect this condition is to check
    * whether parsePendingCatalogs() has been called during the execution of parseCatalog(URL).
    * @group scope
    */
  override protected def parsePendingCatalogs(): Unit = {
    require(isParsing)
    requireState(parsingCatalog.nonEmpty)
    super.parsePendingCatalogs()
    isParsing = false
    parsedCatalogs ++= parsingCatalog
    parsingCatalog = None
  }

  /**
    * Resolves a URI stripped of the trailing '/', if any, with the extension appended to a file path.
    * The resolved path may or may not be an existing file.
    *
    * @param uri uri should match one of the catalog rewrite uri start prefixes to resolve to a file path.
    * @param extension file extension, starting with '.'
    * @return A resolved file path, if the uri matches a rewrite rule
    * @group scope
    */
  def resolveURIWithExtension(uri: String, extension: String): Option[Path] = {
    val uriWithExtension = uri+extension
    Option.apply(resolveURI(uriWithExtension)) match {
      case Some(resolved) =>
        if (resolved.startsWith("file:"))
          Some(Path(resolved.substring(5)))
        else
          None
      case _ =>
        None
    }
  }

  /**
    * Get the catalog entries read from the successfully parsed catalogs.
    * @return catalog entries
    *a
    * @see https://xerces.apache.org/xml-commons/components/apidocs/resolver/org/apache/xml/resolver/CatalogEntry.html
    * @group scope
    */
  def entries(): Seq[CatalogEntry]
  = catalogEntries.asInstanceOf[java.util.Vector[CatalogEntry]].asScala.to[Seq]

  /**
    * Local file paths resolvable given the [[org.apache.xml.resolver.CatalogEntry]] rules according to a predicate.
    *
    * @param predicate
    * @return A map of local files to the longest "uriStartString" with which it was resolved
    *         from the corresponding "rewritePrefix" based on the predicate.
    * @group scope
    */
  def localFileScope(predicate: CatalogEntryFilePredicate): Map[Path, (Path, String)]
  = {
    implicit val catalogRewritePriority: Ordering[Path]
    = new Ordering[Path] {

      override def compare(x: Path, y: Path): Int =
        x.toString.length.compare(y.toString.length) match {
          case 0 =>
            x.toString.compare(y.toString)
          case c =>
            c
        }

    }

    val rewritePaths: SortedMap[Path, String] = entries().foldLeft(SortedMap.empty[Path, String]) {
      case (acc, entry: CatalogEntry) if Catalog.REWRITE_URI == entry.getEntryType =>

        val rewriteEntry
        : Option[(Path, String)]
        = nonFatalCatch[Option[(Path, String)]]
          .withApply { _ => None }
          .apply {
            val uriStartString = entry.getEntryArg(0)
            val rewritePrefix = new File(new URL(entry.getEntryArg(1)).toURI.getPath)
            if (rewritePrefix.exists || rewritePrefix.getParentFile.exists) {
              val pathPrefix = Path(rewritePrefix)
              Some(pathPrefix -> uriStartString)
            } else
              None
          }

        rewriteEntry.fold(acc) { case (rewritePath, uriStartString) =>

          if (acc.contains(rewritePath))
            acc
          else
            acc + (rewritePath -> uriStartString)
        }

      case (acc, _) =>
        acc
    }

    rewritePaths.foldLeft(Map.empty[Path, (Path, String)]) { case (acc, (pathPrefix, uriStartString)) =>
      val localFiles: Set[Path]
      = if (pathPrefix.toIO.exists && pathPrefix.isDir)
        ls
          .rec((_: Path) => false)(pathPrefix)
          .filter(f => predicate(uriStartString, f))
          .to[Set]
      else
        predicate
          .expandLocalFilePath(pathPrefix)
          .filter(f => f.toIO.exists() && f.isFile)

      localFiles.foldLeft(acc) { case (prev, localFile) =>
        prev.get(localFile) match {
          case None =>
            prev + (localFile -> (pathPrefix, uriStartString))
          case Some((pPrefix, uriPrefix)) =>
            if (uriPrefix.length > uriStartString.length)
              prev
            else
              prev.updated(localFile, (pathPrefix, uriStartString))
        }
      }

    }
  }


  /**
    * Converts [[localFileScope()]] to map of IRI and corresponding local file.
    *
    * @param localScopeFiles
    * @param predicate
    * @return Uses the "uriStartString" from the [[localFileScope()]] results to reconstruct IRIs corresponding
    *         to catalog-resolved local files.
    */
  def iri2file(localScopeFiles: Map[Path, (Path, String)], predicate: CatalogEntryFilePredicate)
  : Map[String, Path]
  = localScopeFiles.foldLeft(Map.empty[String, Path]) {
    case (acc1, (f, (pathPrefix, uriStartPrefix))) =>
      val suffix = f.relativeTo(pathPrefix)
      val suffixWithoutExtensions = predicate.fileExtensions.foldLeft(suffix.toString)(_.stripSuffix(_))
      val uri = uriStartPrefix + (if (uriStartPrefix.endsWith("/")) "" else "/") + suffixWithoutExtensions
      acc1 + (uri -> f)
  }
}