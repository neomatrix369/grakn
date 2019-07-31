/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2018 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.graql.executor.property;

import com.google.common.collect.ImmutableSet;
import grakn.core.concept.ConceptId;
import grakn.core.concept.Label;
import grakn.core.concept.thing.Attribute;
import grakn.core.concept.thing.Thing;
import grakn.core.graql.executor.WriteExecutor;
import grakn.core.graql.gremlin.EquivalentFragmentSet;
import grakn.core.graql.reasoner.atom.Atomic;
import grakn.core.graql.reasoner.atom.binary.AttributeAtom;
import grakn.core.graql.reasoner.atom.predicate.IdPredicate;
import grakn.core.graql.reasoner.atom.predicate.ValuePredicate;
import grakn.core.graql.reasoner.query.ReasonerQuery;
import grakn.core.server.kb.Schema;
import graql.lang.property.HasAttributeProperty;
import graql.lang.property.IsaProperty;
import graql.lang.property.VarProperty;
import graql.lang.statement.Statement;
import graql.lang.statement.Variable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static grakn.core.graql.gremlin.sets.EquivalentFragmentSets.neq;
import static grakn.core.graql.gremlin.sets.EquivalentFragmentSets.rolePlayer;
import static grakn.core.graql.reasoner.utils.ReasonerUtils.getIdPredicate;
import static grakn.core.graql.reasoner.utils.ReasonerUtils.getValuePredicates;

public class HasAttributeExecutor implements PropertyExecutor.Insertable {

    private final Variable var;
    private final HasAttributeProperty property;
    private final Label type;

    HasAttributeExecutor(Variable var, HasAttributeProperty property) {
        this.var = var;
        this.property = property;
        this.type = Label.of(property.type());
    }

    @Override
    public Set<EquivalentFragmentSet> matchFragments() {
        Label has = Schema.ImplicitType.HAS.getLabel(type);
        Label key = Schema.ImplicitType.KEY.getLabel(type);

        Label hasOwnerRole = Schema.ImplicitType.HAS_OWNER.getLabel(type);
        Label keyOwnerRole = Schema.ImplicitType.KEY_OWNER.getLabel(type);
        Label hasValueRole = Schema.ImplicitType.HAS_VALUE.getLabel(type);
        Label keyValueRole = Schema.ImplicitType.KEY_VALUE.getLabel(type);

        Variable edge1 = new Variable();
        Variable edge2 = new Variable();

        return ImmutableSet.of(
                //owner rolePlayer edge
                rolePlayer(property, property.relation().var(), edge1, var, null,
                           ImmutableSet.of(hasOwnerRole, keyOwnerRole), ImmutableSet.of(has, key)),
                //value rolePlayer edge
                rolePlayer(property, property.relation().var(), edge2, property.attribute().var(), null,
                           ImmutableSet.of(hasValueRole, keyValueRole), ImmutableSet.of(has, key)),
                neq(property, edge1, edge2)
        );
    }

    @Override
    public Atomic atomic(ReasonerQuery parent, Statement statement, Set<Statement> otherStatements) {
        //NB: HasAttributeProperty always has (type) label specified
        Variable varName = var.asReturnedVar();

        //NB: we always make the attribute variable explicit
        Variable attributeVariable = property.attribute().var().asReturnedVar();
        Variable relationVariable = property.relation().var();
        Variable predicateVariable = new Variable();
        Set<ValuePredicate> predicates = getValuePredicates(attributeVariable, property.attribute(), otherStatements, parent);

        IsaProperty isaProp = property.attribute().getProperties(IsaProperty.class).findFirst().orElse(null);
        Statement typeVar = isaProp != null ? isaProp.type() : null;
        IdPredicate predicate = typeVar != null ? getIdPredicate(predicateVariable, typeVar, otherStatements, parent) : null;
        ConceptId predicateId = predicate != null ? predicate.getPredicate() : null;

        //add resource atom
        Statement resVar = relationVariable.isReturned() ?
                new Statement(varName).has(property.type(), new Statement(attributeVariable), new Statement(relationVariable)) :
                new Statement(varName).has(property.type(), new Statement(attributeVariable));
        return AttributeAtom.create(resVar, attributeVariable, relationVariable,
                                    predicateVariable, predicateId, predicates, parent);
    }

    @Override
    public Set<PropertyExecutor.Writer> insertExecutors() {
        return ImmutableSet.of(new InsertHasAttribute());
    }

    private class InsertHasAttribute implements PropertyExecutor.Writer {

        @Override
        public Variable var() {
            return var;
        }

        @Override
        public VarProperty property() {
            return property;
        }

        @Override
        public Set<Variable> requiredVars() {
            Set<Variable> required = new HashSet<>();
            required.add(var);
            required.add(property.attribute().var());
            return Collections.unmodifiableSet(required);
        }

        @Override
        public Set<Variable> producedVars() {
            return ImmutableSet.of(property.relation().var());
        }

        @Override
        public void execute(WriteExecutor executor) {
            Attribute attributeConcept = executor.getConcept(property.attribute().var()).asAttribute();
            Thing thing = executor.getConcept(var).asThing();
            ConceptId relationId = thing.relhas(attributeConcept).id();
            executor.getBuilder(property.relation().var()).id(relationId);
        }
    }
}
