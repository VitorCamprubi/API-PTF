package br.com.ptf.api.exception;

public class DuplicateDocumentException extends BusinessException {

    public DuplicateDocumentException(String document) {
        super("ja existe conta para o documento " + document);
    }
}
