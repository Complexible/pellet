// Copyright (c) 2006 - 2010, Clark & Parsia, LLC. <http://www.clarkparsia.com>
// This source code is available under the terms of the Affero General Public License v3.
//
// Please see LICENSE.txt for full license terms, including the availability of proprietary exceptions.
// Questions, comments, or requests for clarification: licensing@clarkparsia.com

package com.clarkparsia.pellet.rules.rete;

import com.clarkparsia.pellet.rules.builtins.BuiltIn;
import java.util.Arrays;
import org.mindswap.pellet.ABox;
import org.mindswap.pellet.Literal;
import org.mindswap.pellet.utils.ATermUtils;

public class BuiltInCondition implements FilterCondition
{
	private final ABox abox;
	private final String name;
	private final BuiltIn builtin;
	private final NodeProvider[] args;

	public BuiltInCondition(final ABox abox, final String name, final BuiltIn builtin, final NodeProvider[] args)
	{
		this.abox = abox;
		this.name = name;
		this.builtin = builtin;
		this.args = args;
		for (final NodeProvider arg : args)
			if (arg == null)
				throw new NullPointerException();
	}

	@Override
	public boolean test(final WME wme, final Token token)
	{
		final Literal[] literals = new Literal[args.length];
		for (int i = 0; i < literals.length; i++)
			literals[i] = (Literal) args[i].getNode(wme, token);
		return builtin.apply(abox, literals);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(args);
		result = prime * result + builtin.hashCode();
		return result;
	}

	@Override
	public boolean equals(final Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof BuiltInCondition))
			return false;
		final BuiltInCondition other = (BuiltInCondition) obj;
		return builtin.equals(other.builtin) && Arrays.equals(args, other.args);
	}

	@Override
	public String toString()
	{
		return ATermUtils.toString(ATermUtils.makeTermAppl(name)) + Arrays.toString(args);
	}
}
