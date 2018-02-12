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

import ammonite.ops.Path
import scala.collection.immutable.Set

/**
  * A CatalogEntryFilePredicate is interface for calculating the local file scope of a catalog
  * by filtering rewrite rules (apply method) and expanding local paths to yield candidate local files.
  * @group scope
  */
trait CatalogEntryFilePredicate {

  /**
    * Filters a path according to a CatalogEntry rewriteURI rule
    *
    * @param uriStartString The rule's uriStartString
    * @param path A path that starts with the rule's rewritePrefix
    * @return
    * @group scope
    */
  def apply(uriStartString: String, path: Path): Boolean

  /**
    * Expands a path prefix to yield potentially zero or more candidate local files.
    *
    * @param pathPrefix
    * @return
    * @group scope
    */
  def expandLocalFilePath(pathPrefix: Path): Set[Path]

  /**
    * File extension including '.'
    */
  val fileExtension: String
}
