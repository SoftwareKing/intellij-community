/*
 * Copyright 2005 Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public class IndexOfReplaceableByContainsInspection
        extends ExpressionInspection {

    public String getDisplayName() {
        return "'.indexOf()' expression is replaceable by '.contains()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiBinaryExpression expression = (PsiBinaryExpression)location;
        final PsiExpression lhs = expression.getLOperand();
        final PsiJavaToken sign = expression.getOperationSign();
        final String text;
        if (lhs instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)lhs;
            text = createContainsExpressionText(callExpression, sign, false);
        } else {
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)expression.getROperand();
            assert callExpression != null;
            text = createContainsExpressionText(callExpression, sign, true);
        }
        return "'#ref' can be replaced by '" + text + "' #loc";
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return new IndexOfReplaceableByContainsFix();
    }

    private static class IndexOfReplaceableByContainsFix
            extends InspectionGadgetsFix {

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiBinaryExpression expression =
                    (PsiBinaryExpression)descriptor.getPsiElement();
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression rhs = expression.getROperand();
            final PsiJavaToken sign = expression.getOperationSign();
            final String newExpressionText;
            if (lhs instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression callExpression =
                        (PsiMethodCallExpression)lhs;
                newExpressionText =
                        createContainsExpressionText(callExpression, sign,
                                false);
            } else {
                final PsiMethodCallExpression callExpression =
                        (PsiMethodCallExpression)rhs;
                assert callExpression != null;
                newExpressionText =
                createContainsExpressionText(callExpression, sign, true);
            }
            replaceExpression(expression, newExpressionText);
        }

        public String getName() {
            return "Replace '.indexOf()' with '.contains()'";
        }
    }

    static String createContainsExpressionText(
            @NotNull PsiMethodCallExpression call,
            @NotNull PsiJavaToken sign,
            boolean flipped) {
        final IElementType tokenType = sign.getTokenType();
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression
                .getQualifierExpression();
        final PsiExpressionList argumentList = call.getArgumentList();
        assert argumentList != null;
        final PsiExpression expression = argumentList.getExpressions()[0];
        final String newExpressionText =
                qualifierExpression.getText() + ".contains(" +
                expression.getText() + ')';
        if (tokenType.equals(JavaTokenType.EQEQ)) {
            return '!' + newExpressionText;
        } else if (!flipped && (tokenType.equals(JavaTokenType.LT) ||
                                tokenType.equals(JavaTokenType.LE))) {
            return '!' + newExpressionText;
        } else if (flipped && (tokenType.equals(JavaTokenType.GT) ||
                               tokenType.equals(JavaTokenType.GE))) {
            return '!' + newExpressionText;
        }
        return newExpressionText;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new IndexOfReplaceableByContainsVisitor();
    }

    private static class IndexOfReplaceableByContainsVisitor
            extends BaseInspectionVisitor {
        public void visitBinaryExpression(PsiBinaryExpression expression) {
            final PsiManager manager = expression.getManager();
            final LanguageLevel languageLevel =
                    manager.getEffectiveLanguageLevel();
            if(languageLevel.equals(LanguageLevel.JDK_1_3) ||
               languageLevel.equals(LanguageLevel.JDK_1_4)){
                return;
            }
            super.visitBinaryExpression(expression);
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            if (!ComparisonUtils.isComparison(expression)) {
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            if (lhs instanceof PsiMethodCallExpression) {
                final PsiJavaToken sign = expression.getOperationSign();
                if (canBeReplacedByContains(lhs, sign, rhs, false)) {
                    registerError(expression);
                }
            } else if (rhs instanceof PsiMethodCallExpression) {
                final PsiJavaToken sign = expression.getOperationSign();
                if (canBeReplacedByContains(rhs, sign, lhs, true)) {
                    registerError(expression);
                }
            }
        }

        private static boolean canBeReplacedByContains(PsiExpression lhs,
                                                       PsiJavaToken sign,
                                                       PsiExpression rhs,
                                                       boolean flipped) {
            final PsiManager manager = lhs.getManager();
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression)lhs;
            if (!isIndexOfCall(callExpression)) {
                return false;
            }
            final PsiConstantEvaluationHelper constantEvaluationHelper =
                    manager.getConstantEvaluationHelper();
            final Object object =
                    constantEvaluationHelper.computeConstantExpression(rhs);
            if (!(object instanceof Integer)) {
                return false;
            }
            final Integer constant = (Integer)object;
            final IElementType tokenType = sign.getTokenType();
            if (flipped) {
                if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                                       JavaTokenType.LT.equals(tokenType) ||
                                       JavaTokenType.EQEQ.equals(tokenType) ||
                                       JavaTokenType.GE.equals(tokenType))) {
                    return true;
                } else if (constant == 0 &&
                           (JavaTokenType.LE.equals(tokenType) ||
                            JavaTokenType.GT.equals(tokenType))) {
                    return true;
                }
            } else {
                if (constant == -1 && (JavaTokenType.NE.equals(tokenType) ||
                                       JavaTokenType.GT.equals(tokenType) ||
                                       JavaTokenType.EQEQ.equals(tokenType) ||
                                       JavaTokenType.LE.equals(tokenType))) {
                    return true;
                } else if (constant == 0 &&
                           (JavaTokenType.GE.equals(tokenType) ||
                            JavaTokenType.LT.equals(tokenType))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isIndexOfCall(
                @NotNull PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (!"indexOf".equals(methodName)) {
                return false;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if (argumentList == null) {
                return false;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if (args.length != 1) {
                return false;
            }
            final PsiType type = args[0].getType();
            if (!type.equalsToText("java.lang.String")) {
                return false;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if (qualifier == null) {
                return false;
            }
            final PsiType qualifierType = qualifier.getType();
            return qualifierType.equalsToText("java.lang.String");
        }
    }
}