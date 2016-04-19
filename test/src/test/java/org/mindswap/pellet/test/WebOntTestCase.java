// Copyright (c) 2006 - 2008, Clark & Parsia, LLC. <http://www.clarkparsia.com>
// This source code is available under the terms of the Affero General Public License v3.
//
// Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
// Questions, comments, or requests for clarification: licensing@clarkparsia.com

package org.mindswap.pellet.test;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

public class WebOntTestCase
{
	File manifest;
	WebOntTest test;

	public WebOntTestCase(final WebOntTest test, final File manifest, @SuppressWarnings("unused") final String name)
	{
		//        super( "OWLTestCase-" + name );
		this.test = test;
		this.manifest = manifest;
	}

	public void runTest() throws IOException
	{
		assertTrue(test.doSingleTest(manifest.toURI().toURL().toString()) != WebOntTest.TEST_FAIL);
	}

	@Override
	public String toString()
	{
		return manifest.getParentFile().getName() + "/" + manifest.getName();
	}
}
