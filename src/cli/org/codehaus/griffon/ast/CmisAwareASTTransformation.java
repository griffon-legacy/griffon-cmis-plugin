/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.griffon.ast;

import griffon.plugins.cmis.DefaultCmisProvider;
import griffon.plugins.cmis.CmisAware;
import griffon.plugins.cmis.CmisContributionHandler;
import griffon.plugins.cmis.CmisProvider;
import lombok.core.handlers.CmisAwareConstants;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.codehaus.griffon.ast.GriffonASTUtils.*;

/**
 * Handles generation of code for the {@code @CmisAware} annotation.
 * <p/>
 *
 * @author Andres Almiray
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class CmisAwareASTTransformation extends AbstractASTTransformation implements CmisAwareConstants {
    private static final Logger LOG = LoggerFactory.getLogger(CmisAwareASTTransformation.class);
    private static final ClassNode CMIS_CONTRIBUTION_HANDLER_CNODE = makeClassSafe(CmisContributionHandler.class);
    private static final ClassNode CMIS_AWARE_CNODE = makeClassSafe(CmisAware.class);
    private static final ClassNode CMIS_PROVIDER_CNODE = makeClassSafe(CmisProvider.class);
    private static final ClassNode DEFAULT_CMIS_PROVIDER_CNODE = makeClassSafe(DefaultCmisProvider.class);

    private static final String[] DELEGATING_METHODS = new String[] {
        METHOD_WITH_CMIS
    };

    static {
        Arrays.sort(DELEGATING_METHODS);
    }

    /**
     * Convenience method to see if an annotated node is {@code @CmisAware}.
     *
     * @param node the node to check
     * @return true if the node is an event publisher
     */
    public static boolean hasCmisAwareAnnotation(AnnotatedNode node) {
        for (AnnotationNode annotation : node.getAnnotations()) {
            if (CMIS_AWARE_CNODE.equals(annotation.getClassNode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles the bulk of the processing, mostly delegating to other methods.
     *
     * @param nodes  the ast nodes
     * @param source the source unit for the nodes
     */
    public void visit(ASTNode[] nodes, SourceUnit source) {
        checkNodesForAnnotationAndType(nodes[0], nodes[1]);
        addCmisContributionIfNeeded(source, (ClassNode) nodes[1]);
    }

    public static void addCmisContributionIfNeeded(SourceUnit source, ClassNode classNode) {
        if (needsCmisContribution(classNode, source)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Injecting " + CmisContributionHandler.class.getName() + " into " + classNode.getName());
            }
            apply(classNode);
        }
    }

    protected static boolean needsCmisContribution(ClassNode declaringClass, SourceUnit sourceUnit) {
        boolean found1 = false, found2 = false, found3 = false, found4 = false;
        ClassNode consideredClass = declaringClass;
        while (consideredClass != null) {
            for (MethodNode method : consideredClass.getMethods()) {
                // just check length, MOP will match it up
                found1 = method.getName().equals(METHOD_WITH_CMIS) && method.getParameters().length == 1;
                found2 = method.getName().equals(METHOD_WITH_CMIS) && method.getParameters().length == 2;
                found3 = method.getName().equals(METHOD_SET_CMIS_PROVIDER) && method.getParameters().length == 1;
                found4 = method.getName().equals(METHOD_GET_CMIS_PROVIDER) && method.getParameters().length == 0;
                if (found1 && found2 && found3 && found4) {
                    return false;
                }
            }
            consideredClass = consideredClass.getSuperClass();
        }
        if (found1 || found2 || found3 || found4) {
            sourceUnit.getErrorCollector().addErrorAndContinue(
                new SimpleMessage("@CmisAware cannot be processed on "
                    + declaringClass.getName()
                    + " because some but not all of methods from " + CmisContributionHandler.class.getName() + " were declared in the current class or super classes.",
                    sourceUnit)
            );
            return false;
        }
        return true;
    }

    public static void apply(ClassNode declaringClass) {
        injectInterface(declaringClass, CMIS_CONTRIBUTION_HANDLER_CNODE);

        // add field:
        // protected CmisProvider this$cmisProvider = DefaultCmisProvider.instance
        FieldNode providerField = declaringClass.addField(
            CMIS_PROVIDER_FIELD_NAME,
            ACC_PRIVATE | ACC_SYNTHETIC,
            CMIS_PROVIDER_CNODE,
            defaultCmisProviderInstance());

        // add method:
        // CmisProvider getCmisProvider() {
        //     return this$cmisProvider
        // }
        injectMethod(declaringClass, new MethodNode(
            METHOD_GET_CMIS_PROVIDER,
            ACC_PUBLIC,
            CMIS_PROVIDER_CNODE,
            Parameter.EMPTY_ARRAY,
            NO_EXCEPTIONS,
            returns(field(providerField))
        ));

        // add method:
        // void setCmisProvider(CmisProvider provider) {
        //     this$cmisProvider = provider ?: DefaultCmisProvider.instance
        // }
        injectMethod(declaringClass, new MethodNode(
            METHOD_SET_CMIS_PROVIDER,
            ACC_PUBLIC,
            ClassHelper.VOID_TYPE,
            params(
                param(CMIS_PROVIDER_CNODE, PROVIDER)),
            NO_EXCEPTIONS,
            block(
                ifs_no_return(
                    cmp(var(PROVIDER), ConstantExpression.NULL),
                    assigns(field(providerField), defaultCmisProviderInstance()),
                    assigns(field(providerField), var(PROVIDER))
                )
            )
        ));

        for (MethodNode method : CMIS_CONTRIBUTION_HANDLER_CNODE.getMethods()) {
            if (Arrays.binarySearch(DELEGATING_METHODS, method.getName()) < 0) continue;
            List<Expression> variables = new ArrayList<Expression>();
            Parameter[] parameters = new Parameter[method.getParameters().length];
            for (int i = 0; i < method.getParameters().length; i++) {
                Parameter p = method.getParameters()[i];
                parameters[i] = new Parameter(makeClassSafe(p.getType()), p.getName());
                parameters[i].getType().setGenericsTypes(p.getType().getGenericsTypes());
                variables.add(var(p.getName()));
            }
            ClassNode returnType = makeClassSafe(method.getReturnType());
            returnType.setGenericsTypes(method.getReturnType().getGenericsTypes());
            returnType.setGenericsPlaceHolder(method.getReturnType().isGenericsPlaceHolder());

            MethodNode newMethod = new MethodNode(
                method.getName(),
                ACC_PUBLIC,
                returnType,
                parameters,
                NO_EXCEPTIONS,
                returns(call(
                    field(providerField),
                    method.getName(),
                    args(variables)))
            );
            newMethod.setGenericsTypes(method.getGenericsTypes());
            injectMethod(declaringClass, newMethod);
        }
    }

    private static Expression defaultCmisProviderInstance() {
        return call(DEFAULT_CMIS_PROVIDER_CNODE, "getInstance", NO_ARGS);
    }
}
