package com.blinkbox.test.Comments;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.builder.CompareToBuilder;

import com.gc.android.market.api.MarketSession;
import com.gc.android.market.api.MarketSession.Callback;
import com.gc.android.market.api.model.Market.Comment;
import com.gc.android.market.api.model.Market.CommentsRequest;
import com.gc.android.market.api.model.Market.CommentsResponse;
import com.gc.android.market.api.model.Market.ResponseContext;

public class CommentFetcher {
	/**
	 *usage: CommentFetcher
		 -e,--excel            Use EXCEL format
		 -f,--filename <arg>   File name to save comments to, will append
		 -fa,--all             Fetch all available comments, One of fx is required
		 -fd,--date <arg>      Fetch by date (takes a timestamp, One of fx is required)
		 -fn,--number <arg>    Fetch specified number comments, One of fx is required
		 -fr,--range <arg>     Fetch range example "1 20" will return the first 20 records, use -1 to go to the end. One of fx is required
		 -fu,--update          Fetch latest, use this to update the file, One of fx is required
		 -h,--help             Display usage
		 -i,--package <arg>    Package name of the app, default is com.we7.player
		 -m,--recovery <arg>   Time to wait before another request when a 429 has been issued
		 -p,--password <arg>   Password of the google account
		 -s,--sort             Sort on multiple columns (takes a comma delimited string of zero based numbers e.g. 2,1,3)
		 -t,--throttle <arg>   Throttle time between requests, necessary
		 -u,--username <arg>   Username of the google account
	 */
	
	private static final int REQUEST_COMMENT_LIMIT=10;
	private static final int ERROR=1;
	private static final  String FETCHED_MSG = "Fetched %d of %d comments";
	private static final  String FETCHED_UPDATE_MSG = "Fetched %d comments since last update";
	private static final  String EXCEPTION_MSG = "RuntimeException caught. Waiting %d ms before retrying request";
	private static CSVPrinter mPrinter = null;
	private static String mPackageName = "com.we7.player";
	private static CSVFormat mCVSFormat = CSVFormat.DEFAULT;
	private static String mFileName;
	private static String mUserName;
	private static String mPassword;
	private static int mThrottleTime = 4000;
	private static int mRecoveryTime = 60000;
	private static final int NOT_FOUND=-1;
	private static int matchIndex=NOT_FOUND;
	private static final int COLUMN_TIMESTAMP=4;
	private static final int COLUMN_RATING=0;
	private static int mSortOrder[]={COLUMN_TIMESTAMP, COLUMN_RATING};
	private static boolean mSort = false;
	private static int mFetched=0;
	private enum FetchMode {ALL, UPDATE, DATE, RANGE, NUMBER};
	private static FetchMode mFetchMode;
	private static int mStartRange;
	private static int mEndRange=-1;
	private static long mDate;
	private static int mNumberOfCommentsToFetch=-1;
	private static int mTotalToFetch=-1;

	private static final String mEmailFrom = "autotest";
	private static String mEmailTo = "";
	private static final String mHost = "localhost";
	private static final String mSubject = "Low Ratings";
	public static int mAlertRating = 2;
	private List<Comment> mBadCommentsList = new ArrayList<Comment>() ;

	public void getCommentsNumber(final String pUserName, final String pPassword, final String pPackageName, final int pNumberOfCommentToFetch, 
			final String pFileName, final CSVFormat pFormat, final int pRequestThrottle){
		getComments(pUserName, pPassword, mPackageName, 0, pNumberOfCommentToFetch, mFileName, mCVSFormat, mThrottleTime, mRecoveryTime);
	}
	
	public void getCommentsAll(final String pUserName, final String pPassword, final String pPackageName, final String pFileName,
			final CSVFormat pFormat, final int pRequestThrottle, final int pTimeout){
		getComments(pUserName, pPassword, mPackageName, 0, 4500, mFileName, mCVSFormat, mThrottleTime, mRecoveryTime);
	}
	
	public void getCommentsRange(final String pUserName, final String pPassword, final String pPackageName, final int pStartIndex, final int pEndIndex,
			final String pFileName, final CSVFormat pFormat, final int pRequestThrottle){
		int startIndex = pStartIndex;
		int endIndex = pEndIndex;
		getComments(pUserName, pPassword, mPackageName, --startIndex, endIndex, mFileName, mCVSFormat, mThrottleTime, mRecoveryTime);
	}
	
	public void updateComments(final String pUserName, final String pPassword, final String pPackageName, final String pFileName,
			final CSVFormat pFormat, final int pRequestThrottle, final int pTimeout, final String pEmailTo, final int pAlertRating){
		final long timestamp = getLatestTimeStampFromFile(pFileName, pFormat, CommentFetcher.COLUMN_TIMESTAMP);
		Timestamp stamp = new Timestamp(timestamp);
		Date date = new Date(stamp.getTime());
		if(timestamp == 0){
			System.out.println("Could not find timestamp!");
			System.exit(ERROR);
		}
		System.out.println(String.format("Last update was: timestamp=[%d] date=[%s]", timestamp, date.toString()));
		getCommentsDate(pUserName, pPassword, pPackageName, timestamp, pFileName, pFormat, pRequestThrottle, pTimeout, pEmailTo, pAlertRating);
	}
		
	private void getComments(final String pUserName, final String pPassword, final String pPackageName, final int pStartRange, final int pEndRange, 
			final String pFileName, final CSVFormat pFormat, final int pRequestThrottle, final int pTimeout){
		MarketSession session = new MarketSession();
		mStartRange = pStartRange;
		mFetched=0;
		session.login(pUserName, pPassword);
		do {
			CommentsRequest commentsRequest = CommentsRequest.newBuilder()
				.setAppId(pPackageName)
				.setStartIndex(mStartRange)
				.setEntriesCount(REQUEST_COMMENT_LIMIT)
				.build();
			session.append(commentsRequest, new Callback<CommentsResponse>() {
				@Override
				public void onResult(ResponseContext context, CommentsResponse response) {
					if (response !=null && response.getCommentsList() !=null){
						mEndRange = pEndRange == -1 ? response.getEntriesCount() : pEndRange;
						mTotalToFetch = mEndRange - pStartRange; 
						if (response.getCommentsList().size() > 0){
							List<Comment> writeList = new ArrayList<Comment>();
							int totalSize = response.getCommentsList().size() + mStartRange;
							if(totalSize > mEndRange){
								int numberToFetch = mEndRange - mStartRange;
								writeList.addAll(response.getCommentsList().subList(0, numberToFetch));
								mStartRange += writeList.size();
							}else{
								writeList.addAll(response.getCommentsList());
								mStartRange += writeList.size();
							}
							mFetched += writeList.size();
							int minsRemaining = -1;
							int secsRemaining = (((mTotalToFetch-mFetched)*(pRequestThrottle/1000)/10)+60);
							System.out.println(getConsoleOutput(minsRemaining, secsRemaining));
							appendCommentData(writeList, pFileName, pFormat);
						}
					}
				 }
			});
			if (session != null){
				try {
					session.flush();
					try {
						Thread.sleep(pRequestThrottle);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} catch (RuntimeException  ex) { //JUST TRAP ALL FOR NOW
					ex.printStackTrace();
					System.out.println(String.format(CommentFetcher.EXCEPTION_MSG, pTimeout));
					try {
						Thread.sleep(pTimeout);
					} catch (InterruptedException e) {
						System.out.println(e.getMessage());
					}
				}
			 }
		} while ((mEndRange == -1) || (mStartRange < mEndRange));
		
		if(mSort)
			sortAndSaveRecords(pFileName, pFormat);
	}

	
	public String getConsoleOutput(int minsRemaining, int secsRemaining) {
		String consoleOutput = "";
		do {
			minsRemaining++;
			secsRemaining = secsRemaining-60;
			} while ((secsRemaining) >= 60);
		if (minsRemaining>1 && secsRemaining!=0){
			consoleOutput = (String.format(CommentFetcher.FETCHED_MSG,
				mFetched, mTotalToFetch)+", "+minsRemaining+" minutes, "+(int) (Math.floor(secsRemaining))+" seconds remaining");
		} else if (minsRemaining>1 && secsRemaining==0){
			consoleOutput = (String.format(CommentFetcher.FETCHED_MSG,
					mFetched, mTotalToFetch)+", "+minsRemaining+" minutes remaining");
		} else if (minsRemaining==1 && secsRemaining!=0){
			consoleOutput = (String.format(CommentFetcher.FETCHED_MSG,
					mFetched, mTotalToFetch)+", "+minsRemaining+" minute, "+(int) (Math.floor(secsRemaining))+" seconds remaining");
		} else if (minsRemaining==1 && secsRemaining==0){
			consoleOutput = (String.format(CommentFetcher.FETCHED_MSG,
					mFetched, mTotalToFetch)+", "+minsRemaining+" minute remaining");
		} else if (minsRemaining==0 && secsRemaining!=0){
			consoleOutput = (String.format(CommentFetcher.FETCHED_MSG,
					mFetched, mTotalToFetch)+", "+(int) (Math.floor(secsRemaining))+" seconds remaining");
		} else if (minsRemaining==0 && secsRemaining==0){
			consoleOutput = ("almost there I promise");
		} else {
			consoleOutput = "bored yet?";
		}
		return consoleOutput;
	}
	
	public int findIndexInBatch(final long pTimeStamp, List<Comment> pComents){
		int index=-1, i=0;
		for(Comment comment : pComents){
			if (comment.getCreationTime() == pTimeStamp){
				index = i;
				break;
			}
			i++;
		}	  
		return index;
	}
	
	public void getCommentsDate(final String pUserName, final String pPassword, final String pPackageName, final long pTimeStamp, final String pFileName,
			final CSVFormat pFormat, final int pRequestThrottle, final int pTimeout, final String pEmailTo, final int pAlertRating){
		MarketSession session = new MarketSession();
			
		session.login(pUserName, pPassword);
		
		do {
			CommentsRequest commentsRequest = CommentsRequest.newBuilder()
				.setAppId(pPackageName)
				.setStartIndex(mFetched)
				.setEntriesCount(REQUEST_COMMENT_LIMIT)
				.build();
			session.append(commentsRequest, new Callback<CommentsResponse>() {
				@Override
				public void onResult(ResponseContext context, CommentsResponse response) {
					if (response !=null && response.getCommentsList() !=null){
						mEndRange = response.getEntriesCount();
						if (response.getCommentsList().size() > 0){
							List<Comment> writeList = null;
							matchIndex = findIndexInBatch(pTimeStamp, response.getCommentsList()); //FIND TIMESTAMP MATCH
							if (matchIndex == NOT_FOUND){
								writeList = response.getCommentsList();
								mFetched += writeList.size();
							}else{
								if(matchIndex > 0){ // DONT NEED THE LAST ONE
									writeList = response.getCommentsList().subList(0, matchIndex);
									mFetched += writeList.size();
								}
							}
							if (writeList != null){
								appendCommentData(writeList, pFileName, pFormat);
								System.out.println(String.format(CommentFetcher.FETCHED_UPDATE_MSG, 
										writeList.size()));
								mBadCommentsList.addAll(getBadComments(writeList, mAlertRating));
							}
						}
					}  
				 }
			});
			if (session != null){
				try {
					session.flush();
					try {
						Thread.sleep(pRequestThrottle);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				} catch (RuntimeException  ex) {
					ex.printStackTrace();
					System.out.println(String.format(CommentFetcher.EXCEPTION_MSG, pTimeout));
					try {
						Thread.sleep(pTimeout);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			 }
		} while (matchIndex == -1 && mFetched < mEndRange);
		//TODO Sending email
		sendMail(mBadCommentsList, mEmailFrom, mEmailTo, mHost, mSubject, mAlertRating);
		if(mSort)
			sortAndSaveRecords(pFileName, pFormat);
	}
	
	//====================================================MAIN====================================================
	
		public static void main(String[] args) {
			CommentFetcher commentFetcher = new CommentFetcher();
			commentFetcher.parseOptions(args);
			switch (mFetchMode) {
	          case ALL:
	        	  System.out.println("Fetching ALL comments");
	        	  commentFetcher.getCommentsAll(mUserName, mPassword, mPackageName, mFileName, mCVSFormat, mThrottleTime, mRecoveryTime);
	              break;
	          case UPDATE:
	        	  System.out.println("Updating comments");
	        	  commentFetcher.updateComments(mUserName, mPassword, mPackageName, mFileName, mCVSFormat, mThrottleTime, mRecoveryTime, mEmailTo, mAlertRating);
	              break;
	          case DATE:
	        	  System.out.println("Updating comments by date");
	        	  commentFetcher.getCommentsDate(mUserName, mPassword, mPackageName, mDate, mFileName, mCVSFormat, mThrottleTime, mRecoveryTime, mEmailTo, mAlertRating);
	              break;
	          case RANGE:
	        	  System.out.println("Updating comments by range");
	        	  commentFetcher.getCommentsRange(mUserName, mPassword, mPackageName, mStartRange, mEndRange, mFileName, mCVSFormat, mThrottleTime);
	              break;
	          case NUMBER:
	        	  System.out.println("Updating comments by number");
	        	  commentFetcher.getCommentsNumber(mUserName, mPassword, mPackageName, mNumberOfCommentsToFetch, mFileName, mCVSFormat, mThrottleTime);
	              break;
	          default:
	              break;
	      }
	  	  System.out.println("Finished batch");
	}
	
	public void sortAndSaveRecords(String pFilename, CSVFormat pFormat){
		List<CSVRecord> records = getAllRecords(pFilename, pFormat);
		System.out.println(String.format("Sorting %d records", records.size()));
		sortCommentsList(records, mSortOrder);
		writeRecordData(records, pFilename, pFormat);
	}
	
	public List<CSVRecord> getAllRecords(String pFilename, CSVFormat pFormat){
		List<CSVRecord> sortableRecords = new ArrayList<CSVRecord>();
		Reader in = null;
		try {
			in = new FileReader(pFilename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(ERROR);
		}
		Iterable<CSVRecord> records = null;
		try {
			records = pFormat.parse(in);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(ERROR);
		}
		
		for (CSVRecord record : records) {
			sortableRecords.add(record);
		}
		
		if(in != null){
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(ERROR);
			}	
		}
		return sortableRecords;
	}
	
	public long getLatestTimeStampFromFile(String filename, CSVFormat pFormat, int column){
		long latest=0;
		Reader in = null;
		try {
			in = new FileReader(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(ERROR);
		}
		Iterable<CSVRecord> records = null;
		try {
			records = pFormat.parse(in);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(ERROR);
		}
		for (CSVRecord record : records) {
			long timeStamp = Long.parseLong(record.get(COLUMN_TIMESTAMP));
			if (timeStamp >latest) 
				 latest=timeStamp;
		}
		if(in != null){
			try {
				in.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(ERROR);
			}	
		}
		return latest;
	}
	
	//====================================================SORT LIST COMPARATORS====================================================
	
	public void sortCommentsListByDateAndRating(final List<CSVRecord> pRecords){
		Collections.sort(pRecords, new Comparator<CSVRecord>()  {
			@Override
			public int compare(CSVRecord r1, CSVRecord r2) {
			    return new CompareToBuilder().append(r2.get(COLUMN_RATING), r1.get(COLUMN_RATING))
			    		.append(r2.get(COLUMN_TIMESTAMP), r1.get(COLUMN_TIMESTAMP)).toComparison();  
			}
		});  
	}
	
	public void sortCommentsListByDate(final List<CSVRecord> pRecords){
		Collections.sort(pRecords, new Comparator<CSVRecord>()  {
			@Override
			public int compare(CSVRecord r1, CSVRecord r2) {
			    return new CompareToBuilder().append(r2.get(COLUMN_TIMESTAMP), r1.get(COLUMN_TIMESTAMP))
			    			.toComparison();  
			}
		});  
	}
	
	public void sortCommentsList(final List<CSVRecord> pRecords, final int cols[]){
		Collections.sort(pRecords, new Comparator<CSVRecord>()  {
			@Override
			public int compare(CSVRecord r1, CSVRecord r2) {
				CompareToBuilder sortBuilder = new CompareToBuilder();
				for (int col: cols){
					sortBuilder.append(r2.get(col), r1.get(col));
				}
			    return sortBuilder.toComparison();
			}
		});  
	}

	//====================================================CMD LINE OPTIONS====================================================
		 	
	public Options makeOptions(){
		final Options options = new Options();
		options.addOption("f", "filename", true, "File name to save comments to, will append");
		options.addOption("u", "username", true, "Username of the google account");
		options.addOption("p", "password", true, "Password of the google account");
		options.addOption("i", "package", true, "Package name of the app, default is com.we7.player");
		options.addOption("t", "throttle", true, "Throttle time between requests, necessary");
		options.addOption("s", "sort", false, "Sort on multiple columns (takes a comma delimited string of zero based numbers e.g. 2,1,3)");
		options.addOption("m", "recovery", true, "Time to wait before another request when a 429 has been issued");
		options.addOption("e", "excel", false, "Use EXCEL format");
		options.addOption("eto", "emailTo", true, "Email to");
		options.addOption("r", "ratingBoundary", true, "Send email alert comprising of all star ratings at this number or below (default is 2)");
		options.addOption("h", "help", false, "Display usage");
		OptionGroup commandGroup = new OptionGroup();
		commandGroup.setRequired(true);
		commandGroup.addOption( new Option("fa", "all", false, "Fetch all available comments, One of fx is required"));
		commandGroup.addOption( new Option("fn", "number", true, "Fetch specified number comments, One of fx is required"));
		commandGroup.addOption( new Option("fu", "update", false, "Fetch latest, use this to update the file, One of fx is required"));
		commandGroup.addOption( new Option("fd", "date", true, "Fetch by date (takes a timestamp, One of fx is required)"));
		Option fr = new Option("fr", "range", true, "Fetch range example \"1 20\" will return the first 20 records, use -1 to go to the end. One of fx is required");
		fr.setArgs(2);
		commandGroup.addOption(fr);
		commandGroup.setRequired(true);
		options.addOptionGroup(commandGroup);
		return options;
	}
	
	public void parseOptions(String[] args){
		HelpFormatter formatter = new HelpFormatter();
		final CommandLineParser cmdLineGnuParser = new GnuParser();
		final Options options = makeOptions(); 
		CommandLine commandLine = null;
		try {
			commandLine = cmdLineGnuParser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			formatter.printHelp( "CommentFetcher", options);
			System.exit(ERROR);
		}
		if (commandLine.getOptions().length == 0){
			formatter.printHelp( "CommentFetcher", options);
			System.out.println("0 args supplied");
			System.exit(ERROR);
		}
		
		//====================================================REQUIRED WITH ARG VALUE====================================================
		
		if (commandLine.hasOption('f') && commandLine.getOptionValue('f') != null){
			mFileName = commandLine.getOptionValue('f');  
		}else {
			System.out.println("You must supply a file name");
			formatter.printHelp("CommentFetcher", options);
			System.exit(ERROR);
		}
		if (commandLine.hasOption('u') && commandLine.getOptionValue('u') != null){
			mUserName = commandLine.getOptionValue('u');
		}else {
			System.out.println("You must supply a user name");
			formatter.printHelp("CommentFetcher", options);
			System.exit(ERROR);
		}
		if (commandLine.hasOption('p') && commandLine.getOptionValue('p') != null){
			mPassword = commandLine.getOptionValue('p');  
		}else {
			System.out.println("You must supply a password");
			formatter.printHelp( "CommentFetcher", options);
			System.exit(ERROR);
		}
		
		//====================================================OPTIONAL WITH NO ARG VALUE====================================================

		if (commandLine.hasOption('e')){
			CommentFetcher.mCVSFormat = CSVFormat.EXCEL;
		}
		if (commandLine.hasOption("fa")){
			mFetchMode = FetchMode.ALL;
		}
		if (commandLine.hasOption("fu")){
			mFetchMode = FetchMode.UPDATE;
		}
		if (commandLine.hasOption('h')){
			formatter.printHelp( "CommentFetcher", options);
			System.exit(1);
		}
		
		//====================================================OPTIONAL WITH ARG VALUE====================================================
		
		if (commandLine.hasOption('i')){
			if(commandLine.getOptionValue('i') != null){
				CommentFetcher.mPackageName = commandLine.getOptionValue('i');  
			}else{
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}	
		if (commandLine.hasOption('t')){
			if(commandLine.getOptionValue('t') != null && isNumeric(commandLine.getOptionValue('t'))){
				CommentFetcher.mThrottleTime = Integer.parseInt(commandLine.getOptionValue('t'));
			}else{
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}
		if (commandLine.hasOption('m')){
			if(commandLine.getOptionValue('m') != null && isNumeric(commandLine.getOptionValue('m'))){
				CommentFetcher.mRecoveryTime = Integer.parseInt(commandLine.getOptionValue('m'));
			}else{
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}
		if (commandLine.hasOption('s')){
			mSort=true;
			if(commandLine.getOptionValue('s') != null){
				CommentFetcher.mSortOrder = convertStringArraytoIntArray(commandLine.getOptionValue('s').split(","));
			}
		}
		if (commandLine.hasOption("fd")){
			if(commandLine.getOptionValue("fd") != null){
				mFetchMode = FetchMode.DATE;
				mDate = Long.parseLong(commandLine.getOptionValue("fd"));
			}else{
				System.out.println("You must supply a date argument");
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}
		if (commandLine.hasOption("fr")){
			String [] optionValues = commandLine.getOptionValues("fr");
			if(optionValues != null && optionValues.length == 2){
				mFetchMode = FetchMode.RANGE;
				mStartRange = Integer.parseInt(optionValues[0]);
				mEndRange = Integer.parseInt(optionValues[1]);
			}else{
				System.out.println("You must supply 2 range arguments");
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}
		if (commandLine.hasOption("fn")){
			String number = commandLine.getOptionValue("fn");
			if(number != null){
				mFetchMode = FetchMode.NUMBER;
				mNumberOfCommentsToFetch = Integer.parseInt(number);
			}else{
				System.out.println("You must supply a number");
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}
		if (commandLine.hasOption("eto") && commandLine.hasOption("fu")){
			if(commandLine.getOptionValue("eto") != null){
				mEmailTo = commandLine.getOptionValue("eto");
			}else{
				formatter.printHelp( "CommentFetcher", options);
				System.exit(ERROR);
			}
		}
	}
	
	public int[] convertStringArraytoIntArray(String[] sarray) {
		if (sarray != null) {
			int intarray[] = new int[sarray.length];
			for (int i = 0; i < sarray.length; i++) {
				intarray[i] = (Integer.parseInt(sarray[i]));
			}
			return intarray;
		}
		return null;
	}
			
	public static boolean isNumeric(String str){  
	  try {  
	    Integer.parseInt(str);  
	  }  
	  catch(NumberFormatException nfe){  
	    return false;  
	  }  
	  return true;  
	}

	//====================================================SAVE DATA====================================================
	
	public void closePrinter(){
		if (mPrinter!=null){
			try {
				mPrinter.close();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(ERROR);
			}
		}
	}
		
	public void writeRecordData(List<CSVRecord> pRecords, String pFileName, CSVFormat pFormat){
		try {
			mPrinter = new CSVPrinter(new PrintWriter(pFileName), pFormat);
		} catch (FileNotFoundException e2) {
			e2.printStackTrace();
			System.exit(ERROR);
		}	
		try {
			mPrinter.printRecords(pRecords);
		} catch (IOException e1) {
			  e1.printStackTrace();
		}
		closePrinter();	
	}
	
	public List<Comment> getBadComments(List<Comment> pComents, int pRating){
		List<Comment> badComments = new ArrayList<Comment>(); 
		for(Comment comment : pComents){
			 if (comment.getRating() <= pRating+1){ //don't ask why I have to +1 here, I don't know
				 badComments.add(comment); 
			 }
		}
		return badComments; 		
	}
	
	//TODO Complete
	public void sendMail(final List<Comment> pComments, final String pFrom, String pTo, String pHost, String pSubject, int pAlertRating){
		  if (pComments.size()==0)
			  return;
		  System.out.println("And now to send an email...");
		  
		  Properties props = new Properties();
	        props.put("mail.smtp.host", "smtp.gmail.com");
	        props.put("mail.smtp.auth", "true");
	        props.put("mail.debug", "true");
	        props.put("mail.smtp.port", 25);
	        props.put("mail.smtp.socketFactory.port", 25);
	        props.put("mail.smtp.starttls.enable", "true");
	        props.put("mail.transport.protocol", "smtp");
	        Session mailSession = null;

	        mailSession = Session.getInstance(props,  
	                new javax.mail.Authenticator() {
	            protected PasswordAuthentication getPasswordAuthentication() {
	                return new PasswordAuthentication("we7phone@gmail.com", "we7rocks");
	            }
	        });

	        try {

	            Transport transport = mailSession.getTransport();

	            MimeMessage message = new MimeMessage(mailSession);

	            message.setSubject(pSubject);
	            message.setFrom(new InternetAddress(pFrom));
	            String []to = new String[]{pTo};
	            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to[0]));
	            StringBuilder sb = new StringBuilder();
	            
	            //sb.append("<html>");
	            //sb.append("<head>");
	            sb.append("<title>Comments which have a rating less than or equal to "+mAlertRating);
	            sb.append("</title>");
	            //sb.append("</head>");
	            //sb.append("<body>");
	            sb.append("<table>");
	            
	            sb.append("<tr>");
            	sb.append("<th>Rating</th>");
            	sb.append("<th>Comment</th>");
            	sb.append("<th>Date</th>");
            	sb.append("</tr>");
            	
	            for(Comment comment : pComments){
	            	sb.append("<tr>");
	            	sb.append("<td>");
	                sb.append(comment.getRating());
	                sb.append("</td>");
	                sb.append("<td>");
	                sb.append(comment.getText());
	                sb.append("</td>");
	                sb.append("<td>");
	                Timestamp stamp = new Timestamp(comment.getCreationTime());
	      		  	Date date = new Date(stamp.getTime());
	                sb.append(date);
	                sb.append("</td>");
	                sb.append("\n\n");
	                sb.append("</tr>");
	               }
	            
	            message.setContent(sb.toString(),"text/html");
	            transport.connect();
	            
	            //System.out.println(sb.toString());
	            transport.sendMessage(message,message.getRecipients(Message.RecipientType.TO));
	            transport.close();
	        } catch (Exception exception) {
	        	exception.printStackTrace();
	        }
	}
	
	
	public void appendCommentData(List<Comment> pComents, String pFileName, CSVFormat pFormat){
		try {
			mPrinter = new CSVPrinter(new PrintWriter(new FileWriter(pFileName, true)), pFormat);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(ERROR);
		}
		
	   for(Comment comment : pComents){
		  List<String> commentData = new ArrayList<String>();
		  commentData.add(String.valueOf(comment.getRating()));
		  commentData.add(comment.getText());
		  Timestamp stamp = new Timestamp(comment.getCreationTime());
		  Date date = new Date(stamp.getTime());
		  commentData.add(date.toString());
		  commentData.add(comment.getAuthorName());
		  String createTimeStamp = Long.toString(comment.getCreationTime());
		  commentData.add(createTimeStamp);
		  commentData.add(comment.getAuthorId());
		  try {
			mPrinter.printRecord(commentData);
		  } catch (IOException e1) {
			  e1.printStackTrace();
		  }
	  }
	  closePrinter();
  	}
}
