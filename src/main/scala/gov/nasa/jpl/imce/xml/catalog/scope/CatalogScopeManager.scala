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

import org.apache.xml.resolver.CatalogManager

/**
  * CatalogScopeManager is a CatalogManager configured to use a CatalogScope as its static catalog.
  *
  * This means that calls to 'getCatalog' will always return this static CatalogScope.
  *
  * @see https://xerces.apache.org/xml-commons/components/apidocs/resolver/org/apache/xml/resolver/CatalogManager.html
  */
class CatalogScopeManager extends CatalogManager {

  setUseStaticCatalog(true)
  setCatalogClassName("gov.nasa.jpl.imce.xml.catalog.scope.CatalogScope")

  val privateCatalog: CatalogScope = new CatalogScope
  privateCatalog.setCatalogManager(this)
  privateCatalog.setupReaders()
  privateCatalog.loadSystemCatalogs()

  override def getPrivateCatalog: CatalogScope = privateCatalog

}
