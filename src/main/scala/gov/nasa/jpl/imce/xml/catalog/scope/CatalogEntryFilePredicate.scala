package gov.nasa.jpl.imce.xml.catalog.scope

import ammonite.ops.Path
import scala.collection.immutable.Set

/**
  * A CatalogEntryFilePredicate is interface for calculating the local file scope of a catalog
  * by filtering rewrite rules (apply method) and expanding local paths to yield candidate local files.
  */
trait CatalogEntryFilePredicate {

  /**
    * Filters a path according to a CatalogEntry rewriteURI rule
    *
    * @param uriStartString The rule's uriStartString
    * @param path A path that starts with the rule's rewritePrefix
    * @return
    */
  def apply(uriStartString: String, path: Path): Boolean

  /**
    * Expands a path prefix to yield potentially zero or more candidate local files.
    *
    * @param pathPrefix
    * @return
    */
  def expandLocalFilePath(pathPrefix: Path): Set[Path]

}
