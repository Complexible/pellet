// Copyright (c) 2006 - 2015, Clark & Parsia, LLC. <http://www.clarkparsia.com>
// This source code is available under the terms of the Affero General Public
// License v3.
//
// Please see LICENSE.txt for full license terms, including the availability of
// proprietary exceptions.
// Questions, comments, or requests for clarification: licensing@clarkparsia.com

package com.complexible.pellet.client.reasoner;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.clarkparsia.owlapiv3.BufferingOntologyChangeListener;
import com.clarkparsia.owlapiv3.OWL;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLLogicalEntity;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.reasoner.AxiomNotInProfileException;
import org.semanticweb.owlapi.reasoner.BufferingMode;
import org.semanticweb.owlapi.reasoner.ClassExpressionNotInProfileException;
import org.semanticweb.owlapi.reasoner.FreshEntitiesException;
import org.semanticweb.owlapi.reasoner.FreshEntityPolicy;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.reasoner.IndividualNodeSetPolicy;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.ReasonerInterruptedException;
import org.semanticweb.owlapi.reasoner.TimeOutException;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.semanticweb.owlapi.util.Version;

/**
 * Implementation of {@link OWLReasoner} interface backed by a {@link SchemaReasoner}.
 *
 * @author Evren Sirin
 */
public class SchemaOWLReasoner implements OWLReasoner {
	public static final Logger LOGGER = Logger.getLogger(SchemaOWLReasoner.class.getName());

	private final SchemaReasoner client;

	private final OWLOntology ontology;

	private final BufferingOntologyChangeListener changeListener;

	public SchemaOWLReasoner(OWLOntology ontology, SchemaReasoner client) {
		this.ontology = ontology;
		this.client = client;

		Iterable<OWLOntologyID> ontologies = Iterables.transform(ontology.getImportsClosure(), new Function<OWLOntology, OWLOntologyID>() {
			@Nullable
			@Override
			public OWLOntologyID apply(final OWLOntology ont) {
				return ont.getOntologyID();
			}
		});
        changeListener = new BufferingOntologyChangeListener(ontologies);

		ontology.getOWLOntologyManager().addOntologyChangeListener(changeListener);
	}

	private boolean isFlushed() {
		return !changeListener.isChanged();
	}

	private <O extends OWLObject, T extends OWLEntity> Node<O> executeSingletonQuery(SchemaQuery query, OWLLogicalEntity input) {
		NodeSet<O> result = executeQuery(query, input);
		if (!result.isSingleton()) {
			throw new IllegalArgumentException("A singleton result expected");
		}

		return Iterables.getOnlyElement(result);
	}

	private <O extends OWLObject> NodeSet<O> executeQuery(SchemaQuery query, OWLLogicalEntity input) {
		return client.query(query, input);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void flush() {
		if (!isFlushed()) {
			client.update(changeListener.getAdditions(), changeListener.getRemovals());
			changeListener.reset();
		}
	}

	@Override
	public void dispose() {
		ontology.getOWLOntologyManager().removeOntologyChangeListener(changeListener);
	}
	
	private OWLLogicalEntity requireNamedObject(OWLObject o) {
		if (o instanceof OWLLogicalEntity) {
			return (OWLLogicalEntity) o;
		}
		
		throw new IllegalArgumentException("This reasoner only supports named entities");
	}

	@Override
	public Node<OWLClass> getEquivalentClasses(OWLClassExpression clsC) {
		flush();

		return executeSingletonQuery(SchemaQuery.EQUIVALENT, requireNamedObject(clsC));
	}

	@Override
	public NodeSet<OWLClass> getSubClasses(OWLClassExpression ce, boolean direct) {
		flush();

		return executeQuery(direct
		                    ? SchemaQuery.CHILD
		                    : SchemaQuery.PARENT, requireNamedObject(ce));
	}

	@Override
	public boolean isSatisfiable(OWLClassExpression clsC) {
		return !getUnsatisfiableClasses().getEntities().contains(requireNamedObject(clsC));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLClass> getBottomClassNode() {
		return getEquivalentClasses(OWL.Nothing);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLDataProperty> getBottomDataPropertyNode() {
		return getEquivalentDataProperties(OWL.bottomDataProperty);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLObjectPropertyExpression> getBottomObjectPropertyNode() {
		return getEquivalentObjectProperties(OWL.bottomObjectProperty);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public BufferingMode getBufferingMode() {
		return BufferingMode.BUFFERING;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeSet<OWLClass> getDataPropertyDomains(OWLDataProperty pe, boolean direct)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(SchemaQuery.DOMAIN, pe);
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<OWLLiteral> getDataPropertyValues(OWLNamedIndividual ind, OWLDataProperty pe)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLNamedIndividual> getDifferentIndividuals(OWLNamedIndividual ind)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLClass> getDisjointClasses(OWLClassExpression ce) {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLDataProperty> getDisjointDataProperties(OWLDataPropertyExpression pe)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();

	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLObjectPropertyExpression> getDisjointObjectProperties(OWLObjectPropertyExpression pe)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {		
		flush();

		return executeQuery(SchemaQuery.DISJOINT, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	public Node<OWLDataProperty> getEquivalentDataProperties(OWLDataProperty pe)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeSingletonQuery(SchemaQuery.EQUIVALENT, pe);
	}

	/**
	 * {@inheritDoc}
	 */
	public Node<OWLObjectPropertyExpression> getEquivalentObjectProperties(OWLObjectPropertyExpression pe)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return (Node) executeSingletonQuery(SchemaQuery.EQUIVALENT, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FreshEntityPolicy getFreshEntityPolicy() {
		return FreshEntityPolicy.DISALLOW;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IndividualNodeSetPolicy getIndividualNodeSetPolicy() {
		return IndividualNodeSetPolicy.BY_NAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeSet<OWLNamedIndividual> getInstances(OWLClassExpression ce, boolean direct)
		throws InconsistentOntologyException, ClassExpressionNotInProfileException,
		       FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLObjectPropertyExpression> getInverseObjectProperties(OWLObjectPropertyExpression pe)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeSingletonQuery(SchemaQuery.INVERSE, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeSet<OWLClass> getObjectPropertyDomains(OWLObjectPropertyExpression pe, boolean direct)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(SchemaQuery.DOMAIN, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeSet<OWLClass> getObjectPropertyRanges(OWLObjectPropertyExpression pe, boolean direct)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(SchemaQuery.RANGE, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeSet<OWLNamedIndividual> getObjectPropertyValues(OWLNamedIndividual ind,
	                                                           OWLObjectPropertyExpression pe) throws InconsistentOntologyException,
	                                                                                                  FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<OWLAxiom> getPendingAxiomAdditions() {
		return changeListener.getAdditions();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<OWLAxiom> getPendingAxiomRemovals() {
		return changeListener.getRemovals();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<OWLOntologyChange> getPendingChanges() {
		return Collections.emptyList();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getReasonerName() {
		return "SPARQL-DL";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Version getReasonerVersion() {
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public OWLOntology getRootOntology() {
		return ontology;
	}

	/**
	 * {@inheritDoc}
	 */
	public Node<OWLNamedIndividual> getSameIndividuals(OWLNamedIndividual ind)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLDataProperty> getSubDataProperties(OWLDataProperty pe, boolean direct)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(direct ? SchemaQuery.CHILD
		                           : SchemaQuery.DESCENDANT, pe);
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLObjectPropertyExpression> getSubObjectProperties(OWLObjectPropertyExpression pe,
	                                                                   boolean direct) throws InconsistentOntologyException, FreshEntitiesException,
	                                                                                          ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(direct ? SchemaQuery.CHILD
		                           : SchemaQuery.DESCENDANT, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLClass> getSuperClasses(OWLClassExpression ce, boolean direct)
		throws InconsistentOntologyException, ClassExpressionNotInProfileException,
		       FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(direct ? SchemaQuery.PARENT
		                           : SchemaQuery.ANCESTOR, requireNamedObject(ce));
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLDataProperty> getSuperDataProperties(OWLDataProperty pe, boolean direct)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(direct ? SchemaQuery.PARENT
		                           : SchemaQuery.ANCESTOR, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	public NodeSet<OWLObjectPropertyExpression> getSuperObjectProperties(OWLObjectPropertyExpression pe,
	                                                                     boolean direct) throws InconsistentOntologyException, FreshEntitiesException,
	                                                                                            ReasonerInterruptedException, TimeOutException {
		flush();

		return executeQuery(direct ? SchemaQuery.PARENT
		                           : SchemaQuery.ANCESTOR, requireNamedObject(pe));
	}

	/**
	 * {@inheritDoc}
	 */
	public long getTimeOut() {
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLClass> getTopClassNode() {
		return getEquivalentClasses(OWL.Thing);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLDataProperty> getTopDataPropertyNode() {
		return getEquivalentDataProperties(OWL.topDataProperty);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLObjectPropertyExpression> getTopObjectPropertyNode() {
		return getEquivalentObjectProperties(OWL.topObjectProperty);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public NodeSet<OWLClass> getTypes(OWLNamedIndividual ind, boolean direct)
		throws InconsistentOntologyException, FreshEntitiesException,
		       ReasonerInterruptedException, TimeOutException {
		flush();

		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Node<OWLClass> getUnsatisfiableClasses() throws ReasonerInterruptedException,
	                                                       TimeOutException {
		return getBottomClassNode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void interrupt() {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isConsistent() throws ReasonerInterruptedException, TimeOutException {
		return isSatisfiable(OWL.Thing);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEntailed(OWLAxiom axiom) throws ReasonerInterruptedException,
	                                                 UnsupportedEntailmentTypeException, TimeOutException, AxiomNotInProfileException,
	                                                 FreshEntitiesException {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isEntailed(Set<? extends OWLAxiom> axioms) throws ReasonerInterruptedException,
	                                                                 UnsupportedEntailmentTypeException, TimeOutException, AxiomNotInProfileException,
	                                                                 FreshEntitiesException {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isEntailmentCheckingSupported(AxiomType<?> axiomType) {
		return true;
	}
	/**
	 * {@inheritDoc}
	 */
	public Set<InferenceType> getPrecomputableInferenceTypes() {
		return ImmutableSet.of(InferenceType.CLASS_HIERARCHY);
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isPrecomputed(InferenceType inferenceType) {
		switch (inferenceType) {
			case CLASS_HIERARCHY:
				return isFlushed();
			default:
				return false;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void precomputeInferences(InferenceType... inferenceTypes) throws ReasonerInterruptedException,
	                                                                         TimeOutException, InconsistentOntologyException {
		for (InferenceType inferenceType : inferenceTypes) {
			switch (inferenceType) {
				case CLASS_HIERARCHY:
					flush();
				default:
					break;
			}
		}
	}
}