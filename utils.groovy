// This file is used for organizational scans
//import groovy.transform.Field

jenkins_creds = 'JENKINS_CREDENTIALSID'
String branch_name = env.BRANCH_NAME

def sh_out(cmd){
      sh(returnStdout:true, script: cmd).trim()
}

def getCommitter(){
  cmtrws = this.sh_out('''
      git show -s --format=\'%ce\' | tr -d "'" | cut -d@ -f1
  ''')
  return cmtrws
}

def getRepo(){
	repo = this.sh_out('basename $(git remote show -n origin | grep Push | cut -d: -f2- | rev | cut -c5- | rev)')
	return repo
}

def notifyEmail(String result, String to, def stage_name=null, def errorMessage=null){
    withCredentials([
        [$class: 'UsernamePasswordMultiBinding', credentialsId: jenkins_creds, usernameVariable: 'J_USER', passwordVariable: 'J_PASS'],
        ]){
				def committer = getCommitter()
				def repo = getRepo()
  		  subject = "${result} building repository $repo branch \"$branch_name\" build #${env.BUILD_ID}!"
  		  job_console = "${env.JOB_URL}${env.BUILD_NUMBER}/console"

  		  String jlink = "(<${env.BUILD_URL}|Open>)"
				emailadd = committer+'@company.com'
    		}
    def body = """
              <!DOCTYPE html>
              <html>
              <body>
              <h2> Hi, Jenkins here! </h2>
                          <p><b> Result for branch: </b> $branch_name </p>
                          <p><b> Job URL for more information: <a href=$job_console target="_blank">$job_console</a></b>
                  </body>
          </html>
      """
      //In case of failure - the mail will include the step name that failed
      if (stage_name) {
          body = body.replaceAll("</body>",
                "<p><b> Pipeline failed at stage: </b> ${stage_name}</p></body>")
      }
      if (errorMessage) {
          body = body.replaceAll("</body>",
                "<p><b> The exception was: </b> ${errorMessage}</p></body>")
      }
      //code below sends email using subject and body defined above
          emailext attachLog: true, body: body, subject: subject,
               recipientProviders: [[$class: 'RequesterRecipientProvider']],
               to: committer, mimeType: 'text/html', compressLog: false
}

def run_in_stage(String stage_name, Closure command, String sendTo){

    String ulink = getCommitter()
    String jlink = "(<${env.BUILD_URL}|Open>)"

  println "============================================================"
  stage (stage_name) {
      try {
          command()
          if (currentBuild.result == 'FAILURE') {
              error "Build failed, see log for further details."
          }
          println "============================================================"
      } catch (Exception ex) {
          def except = "${ex}"
          String emailadd = ulink+'@company.com'
          if (currentBuild.result == null) {
            currentBuild.result = "FAILURE" }
						this.notifyStatus(stage_name, currentBuild.result, except)
          echo "Pipeline failed at stage: ${stage_name}"
          throw ex
      }
  }
}

def notifyStatus(stage_name, result, except=null) {
    ulink = getCommitter()
    jlink = "(<${env.BUILD_URL}|Open>)"
		emailadd = ulink+'@company.com'
		print "Debug information:"
    print "!NS ulink:"+ ulink
    print "!NS emailadd:"+ emailadd

		if(result=='SUCCESS') {
			color = 'good'
			message = "Job: *${env.JOB_NAME}* Finished Successfuly! :thumbsup: Committer: <@${ulink}> Log: ${jlink}>"
		}	else {
			color = 'danger'
			message = "Job: *${env.JOB_NAME}* Failed! :thumbsdown: Committer: <@${ulink}> Log: ${jlink}>"
		}
		notifyEmail(currentBuild.result, emailadd, stage_name, except)
    notifySlack(channel, color, message)
}

def notifySlack(String channel, String color, String teamDomain=null, String token=null, String message) {
    slackSend channel: channel, color: "${color}", teamDomain: teamDomain, token: token,
    message: message
}

return this;
