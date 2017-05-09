/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 *
 */

package ai.grakn.graql.internal.gremlin.sets;

import ai.grakn.graql.VarName;
import ai.grakn.graql.internal.gremlin.EquivalentFragmentSet;

import static ai.grakn.graql.internal.gremlin.fragment.Fragments.inIsaCastings;
import static ai.grakn.graql.internal.gremlin.fragment.Fragments.outIsaCastings;

/**
 * @author Felix Chapman
 */
class IsaCastingsFragmentSet extends EquivalentFragmentSet {

    private final VarName casting;
    private final VarName roleType;

    IsaCastingsFragmentSet(VarName casting, VarName roleType) {
        super(outIsaCastings(casting, roleType), inIsaCastings(roleType, casting));
        this.casting = casting;
        this.roleType = roleType;
    }

    public VarName casting() {
        return casting;
    }

    public VarName roleType() {
        return roleType;
    }
}
