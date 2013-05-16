/*
${license-info}
${developer-info}
${author-info}
*/

package org.quattor.ant;

public class ShowVersion  {

  private final static String version = "${project.version}";

  public static void main (String[] args) {

    System.out.println("Version "+version);
  }

}

