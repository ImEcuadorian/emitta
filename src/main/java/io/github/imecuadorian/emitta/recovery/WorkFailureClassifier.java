package io.github.imecuadorian.emitta.recovery;
public final class WorkFailureClassifier {
 public enum Category { TRANSIENT,VALIDATION,INTEGRITY,EXTERNAL_UNKNOWN,PERMANENT }
 public static Category classify(Throwable failure) {
  for(Throwable t=failure;t!=null;t=t.getCause()) {
   if(t instanceof io.github.resilience4j.circuitbreaker.CallNotPermittedException
       || t instanceof io.github.resilience4j.bulkhead.BulkheadFullException) return Category.TRANSIENT;
   if(t instanceof io.github.imecuadorian.emitta.srireception.application.exception.SriReceptionException) return Category.EXTERNAL_UNKNOWN;
   if(t instanceof io.github.imecuadorian.emitta.documentartifact.application.exception.DocumentArtifactIntegrityException
       || t instanceof io.github.imecuadorian.emitta.fiscalsigning.application.exception.XmlSignatureVerificationException) return Category.INTEGRITY;
  }
  for(Throwable t=failure;t!=null;t=t.getCause()) {
   if(t instanceof org.springframework.dao.TransientDataAccessException || t instanceof org.springframework.dao.DataAccessResourceFailureException
       || t instanceof java.io.IOException || t instanceof software.amazon.awssdk.core.exception.SdkClientException) return Category.TRANSIENT;
   if(t instanceof software.amazon.awssdk.awscore.exception.AwsServiceException aws && (aws.statusCode()>=500 || aws.statusCode()==429)) return Category.TRANSIENT;
  }
  for(Throwable t=failure;t!=null;t=t.getCause()) if(t instanceof IllegalArgumentException) return Category.VALIDATION;
  return Category.PERMANENT;
 }
 private WorkFailureClassifier() {}
}
