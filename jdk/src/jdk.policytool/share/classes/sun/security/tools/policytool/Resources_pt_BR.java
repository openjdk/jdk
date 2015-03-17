/*
 * Copyright (c) 2000, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.tools.policytool;

/**
 * <p> This class represents the <code>ResourceBundle</code>
 * for the policytool.
 *
 */
public class Resources_pt_BR extends java.util.ListResourceBundle {

    private static final Object[][] contents = {
        {"NEWLINE", "\n"},
        {"Warning.A.public.key.for.alias.signers.i.does.not.exist.Make.sure.a.KeyStore.is.properly.configured.",
                "Advert\u00EAncia: N\u00E3o existe uma chave p\u00FAblica para o alias {0}. Certifique-se de que um KeyStore esteja configurado adequadamente."},
        {"Warning.Class.not.found.class", "Advert\u00EAncia: Classe n\u00E3o encontrada: {0}"},
        {"Warning.Invalid.argument.s.for.constructor.arg",
                "Advert\u00EAncia: Argumento(s) inv\u00E1lido(s) para o construtor: {0}"},
        {"Illegal.Principal.Type.type", "Tipo Principal Inv\u00E1lido: {0}"},
        {"Illegal.option.option", "Op\u00E7\u00E3o inv\u00E1lida: {0}"},
        {"Usage.policytool.options.", "Uso: policytool [op\u00E7\u00F5es]"},
        {".file.file.policy.file.location",
                "  [-file <arquivo>]    localiza\u00E7\u00E3o do arquivo de pol\u00EDtica"},
        {"New", "Novo"},
        {"Open", "Abrir"},
        {"Save", "Salvar"},
        {"Save.As", "Salvar Como"},
        {"View.Warning.Log", "Exibir Log de Advert\u00EAncias"},
        {"Exit", "Sair"},
        {"Add.Policy.Entry", "Adicionar Entrada de Pol\u00EDtica"},
        {"Edit.Policy.Entry", "Editar Entrada de Pol\u00EDtica"},
        {"Remove.Policy.Entry", "Remover Entrada de Pol\u00EDtica"},
        {"Edit", "Editar"},
        {"Retain", "Reter"},

        {"Warning.File.name.may.include.escaped.backslash.characters.It.is.not.necessary.to.escape.backslash.characters.the.tool.escapes",
            "Advert\u00EAncia: O nome do arquivo pode conter caracteres de escape barra invertida. N\u00E3o \u00E9 necess\u00E1rio fazer o escape dos caracteres de barra invertida (a ferramenta faz o escape dos caracteres conforme necess\u00E1rio ao gravar o conte\u00FAdo da pol\u00EDtica no armazenamento persistente).\n\nClique em Reter para reter o nome da entrada ou clique em Editar para edit\u00E1-lo."},

        {"Add.Public.Key.Alias", "Adicionar Alias de Chave P\u00FAblica"},
        {"Remove.Public.Key.Alias", "Remover Alias de Chave P\u00FAblica"},
        {"File", "Arquivo"},
        {"KeyStore", "KeyStore"},
        {"Policy.File.", "Arquivo de Pol\u00EDtica:"},
        {"Could.not.open.policy.file.policyFile.e.toString.",
                "N\u00E3o foi poss\u00EDvel abrir o arquivo de pol\u00EDtica: {0}: {1}"},
        {"Policy.Tool", "Ferramenta de Pol\u00EDtica"},
        {"Errors.have.occurred.while.opening.the.policy.configuration.View.the.Warning.Log.for.more.information.",
                "Erros durante a abertura da configura\u00E7\u00E3o da pol\u00EDtica. Consulte o Log de Advert\u00EAncias para obter mais informa\u00E7\u00F5es."},
        {"Error", "Erro"},
        {"OK", "OK"},
        {"Status", "Status"},
        {"Warning", "Advert\u00EAncia"},
        {"Permission.",
                "Permiss\u00E3o:                                                       "},
        {"Principal.Type.", "Tipo do Principal:"},
        {"Principal.Name.", "Nome do Principal:"},
        {"Target.Name.",
                "Nome do Alvo:                                                    "},
        {"Actions.",
                "A\u00E7\u00F5es:                                                             "},
        {"OK.to.overwrite.existing.file.filename.",
                "Est\u00E1 correto substituir o arquivo existente {0}?"},
        {"Cancel", "Cancelar"},
        {"CodeBase.", "CodeBase:"},
        {"SignedBy.", "SignedBy:"},
        {"Add.Principal", "Adicionar Principal"},
        {"Edit.Principal", "Editar Principal"},
        {"Remove.Principal", "Remover Principal"},
        {"Principals.", "Principais:"},
        {".Add.Permission", "  Adicionar Permiss\u00E3o"},
        {".Edit.Permission", "  Editar Permiss\u00E3o"},
        {"Remove.Permission", "Remover Permiss\u00E3o"},
        {"Done", "Conclu\u00EDdo"},
        {"KeyStore.URL.", "URL do KeyStore:"},
        {"KeyStore.Type.", "Tipo de KeyStore:"},
        {"KeyStore.Provider.", "Fornecedor de KeyStore:"},
        {"KeyStore.Password.URL.", "URL da Senha do KeyStore:"},
        {"Principals", "Principais"},
        {".Edit.Principal.", "  Editar Principal:"},
        {".Add.New.Principal.", "  Adicionar Novo Principal:"},
        {"Permissions", "Permiss\u00F5es"},
        {".Edit.Permission.", "  Editar Permiss\u00E3o:"},
        {".Add.New.Permission.", "  Adicionar Nova Permiss\u00E3o:"},
        {"Signed.By.", "Assinado por:"},
        {"Cannot.Specify.Principal.with.a.Wildcard.Class.without.a.Wildcard.Name",
            "N\u00E3o \u00E9 Poss\u00EDvel Especificar um Principal com uma Classe de Curinga sem um Nome de Curinga"},
        {"Cannot.Specify.Principal.without.a.Name",
            "N\u00E3o \u00E9 Poss\u00EDvel Especificar um Principal sem um Nome"},
        {"Permission.and.Target.Name.must.have.a.value",
                "O Nome de Destino e a Permiss\u00E3o devem ter um Valor"},
        {"Remove.this.Policy.Entry.", "Remover esta Entrada de Pol\u00EDtica?"},
        {"Overwrite.File", "Substituir Arquivo"},
        {"Policy.successfully.written.to.filename",
                "Pol\u00EDtica gravada com \u00EAxito em {0}"},
        {"null.filename", "nome de arquivo nulo"},
        {"Save.changes.", "Salvar altera\u00E7\u00F5es?"},
        {"Yes", "Sim"},
        {"No", "N\u00E3o"},
        {"Policy.Entry", "Entrada de Pol\u00EDtica"},
        {"Save.Changes", "Salvar Altera\u00E7\u00F5es"},
        {"No.Policy.Entry.selected", "Nenhuma Entrada de Pol\u00EDtica Selecionada"},
        {"Unable.to.open.KeyStore.ex.toString.",
                "N\u00E3o \u00E9 poss\u00EDvel abrir a KeyStore: {0}"},
        {"No.principal.selected", "Nenhum principal selecionado"},
        {"No.permission.selected", "Nenhuma permiss\u00E3o selecionada"},
        {"name", "nome"},
        {"configuration.type", "tipo de configura\u00E7\u00E3o"},
        {"environment.variable.name", "nome da vari\u00E1vel de ambiente"},
        {"library.name", "nome da biblioteca"},
        {"package.name", "nome do pacote"},
        {"policy.type", "tipo de pol\u00EDtica"},
        {"property.name", "nome da propriedade"},
        {"provider.name", "nome do fornecedor"},
        {"url", "url"},
        {"method.list", "lista de m\u00E9todos"},
        {"request.headers.list", "solicitar lista de cabe\u00E7alhos"},
        {"Principal.List", "Lista de Principais"},
        {"Permission.List", "Lista de Permiss\u00F5es"},
        {"Code.Base", "Base de C\u00F3digo"},
        {"KeyStore.U.R.L.", "U R L da KeyStore:"},
        {"KeyStore.Password.U.R.L.", "U R L da Senha do KeyStore:"}
    };


    /**
     * Returns the contents of this <code>ResourceBundle</code>.
     *
     * <p>
     *
     * @return the contents of this <code>ResourceBundle</code>.
     */
    @Override
    public Object[][] getContents() {
        return contents;
    }
}
