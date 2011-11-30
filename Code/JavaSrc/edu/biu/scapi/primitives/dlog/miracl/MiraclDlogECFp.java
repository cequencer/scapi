package edu.biu.scapi.primitives.dlog.miracl;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Properties;
import java.util.logging.Level;

import org.bouncycastle.util.encoders.Hex;

import edu.biu.scapi.exceptions.UnInitializedException;
import edu.biu.scapi.generals.Logging;
import edu.biu.scapi.primitives.dlog.DlogECFp;
import edu.biu.scapi.primitives.dlog.ECElement;
import edu.biu.scapi.primitives.dlog.GroupElement;
import edu.biu.scapi.primitives.dlog.groupParams.ECFpGroupParams;

/**This class implements an Elliptic curve Dlog group over Zp utilizing Miracl's implementation.<p>
 * It uses JNI technology to call Miracl's native code.
 * @author Cryptography and Computer Security Research Group Department of Computer Science Bar-Ilan University (Moriya Farbstein)
 */
public class MiraclDlogECFp extends MiraclAdapterDlogEC implements DlogECFp{

	private native void initFpCurve(long mip, byte[] p, byte[] a,byte[] b);
	private native long multiplyFpPoints(long mip, long p1, long p2);
	private native long exponentiateFpPoint(long mip, long p, byte[] exponent);
	private native long invertFpPoint(long mip, long p);
	private native boolean validateFpGenerator(long mip, long generator, byte[] x, byte[] y);
	private native boolean isFpMember(long mip, long point);
	private native long createInfinityFpPoint(long mip);
	
	public void init(String curveName) throws IllegalArgumentException{
		
		try {
			Properties ecProperties;
		
			ecProperties = getProperties(PROPERTIES_FILES_PATH); //get properties object containing the curve data
		
			//checks that the curveName is in the file
			if(!ecProperties.containsKey(curveName)) { 
				throw new IllegalArgumentException("no such NIST elliptic curve"); 
			} 
			
			//check that the given curve is in the field that matches the group
			if (!curveName.startsWith("P-")){
				throw new IllegalArgumentException("curveName is not a curve over Fp field and doesn't match the DlogGroup type"); 
			}
			isInitialized = true; 
			doInit(ecProperties, curveName);  // set the data and initialize the curve
			
			
		} catch (IOException e) {
			Logging.getLogger().log(Level.WARNING, "error while loading the NIST elliptic curves file");
		}
	}

	/**
	 * Extracts the parameters of the curve from the properties object and initialize the groupParams, 
	 * generator and the underlying curve
	 * @param ecProperties - properties object contains the curve file data
	 * @param curveName - the curve name as it called in the file
	 */
	protected void doInit(Properties ecProperties, String curveName){
		//get the nist parameters
		BigInteger p = new BigInteger(ecProperties.getProperty(curveName));
		BigInteger a = new BigInteger(ecProperties.getProperty(curveName+"a"));
		BigInteger b = new BigInteger(1,Hex.decode(ecProperties.getProperty(curveName+"b")));
		BigInteger x = new BigInteger(1,Hex.decode(ecProperties.getProperty(curveName+"x")));
		BigInteger y = new BigInteger(1,Hex.decode(ecProperties.getProperty(curveName+"y")));
		BigInteger q = new BigInteger(ecProperties.getProperty(curveName+"r"));
		
		//create the GroupParams
		groupParams = new ECFpGroupParams(q, x, y, p, a, b);
		
		//create the curve
		initFpCurve(getMip(), p.toByteArray(), a.mod(p).toByteArray(), b.toByteArray());
		
		//create the generator
		generator = new ECFpPointMiracl(x,y, this);	
	}
	
	/**
	 * @return the type of the group - ECFp
	 */
	public String getGroupType(){
		return "elliptic curve over Fp";
	}
	
	/**
	 * Calculate the inverse of the given GroupElement
	 * @param groupElement to inverse
	 * @return the inverse element of the given GroupElement
	 * @throws IllegalArgumentException
	 * @throws UnInitializedException 
	 */
	public GroupElement getInverse(GroupElement groupElement) throws IllegalArgumentException, UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		//if the GroupElement doesn't match the DlogGroup, throw exception
		if (!(groupElement instanceof ECFpPointMiracl)){
			throw new IllegalArgumentException("groupElement doesn't match the DlogGroup");
		}
		
		//the inverse of infinity point is infinity
		if (((ECFpPointMiracl)groupElement).isInfinity()){
			return groupElement;
		}
		
		long point = ((ECFpPointMiracl)groupElement).getPoint();
		//call to native inverse function
		long result = invertFpPoint(mip, point);
		//build a ECFpPointMiracl element from the result value
		return new ECFpPointMiracl(result, mip);	
		
	}
	
	/**
	 * Multiply two GroupElements
	 * @param groupElement1
	 * @param groupElement2
	 * @return the multiplication result
	 * @throws IllegalArgumentException
	 * @throws UnInitializedException 
	 */
	public GroupElement multiplyGroupElements(GroupElement groupElement1, 
											  GroupElement groupElement2) 
											  throws IllegalArgumentException, UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		//if the GroupElements don't match the DlogGroup, throw exception
		if (!(groupElement1 instanceof ECFpPointMiracl)){
			throw new IllegalArgumentException("groupElement doesn't match the DlogGroup");
		}
		if (!(groupElement2 instanceof ECFpPointMiracl)){
			throw new IllegalArgumentException("groupElement doesn't match the DlogGroup");
		}
		
		//if one of the points is the infinity point, the second one is the multiplication result
		if (((ECFpPointMiracl)groupElement1).isInfinity()){
			return groupElement2;
		}
		if (((ECFpPointMiracl)groupElement2).isInfinity()){
			return groupElement1;
		}
		
		long point1 = ((ECFpPointMiracl)groupElement1).getPoint();
		long point2 = ((ECFpPointMiracl)groupElement2).getPoint();
		
		//call to native multiply function
		long result = multiplyFpPoints(mip, point1, point2);
		//build a ECFpPointMiracl element from the result value
		return new ECFpPointMiracl(result, mip);
		
	}
	
	/**
	 * Calculate the exponentiate of the given GroupElement
	 * @param exponent
	 * @param base 
	 * @return the result of the exponentiation
	 * @throws IllegalArgumentException
	 * @throws UnInitializedException 
	 */
	public GroupElement exponentiate(BigInteger exponent, GroupElement base) 
									 throws IllegalArgumentException, UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		//if the GroupElements don't match the DlogGroup, throw exception
		if (!(base instanceof ECFpPointMiracl)){
			throw new IllegalArgumentException("groupElement doesn't match the DlogGroup");
		}
		
		//infinity remains the same after any exponentiate
		if (((ECFpPointMiracl) base).isInfinity()){
			return base;
		}
		
		long point = ((ECFpPointMiracl)base).getPoint();
		//call to native exponentiate function
		long result = exponentiateFpPoint(mip, point, exponent.toByteArray());
		//build a ECFpPointMiracl element from the result value
		return new ECFpPointMiracl(result, mip);
		
	}
	
	/**
	 * Create a random member of that Dlog group
	 * @return the random element
	 * @throws UnInitializedException 
	 */
	public GroupElement getRandomElement() throws UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		return new ECFpPointMiracl(this);
	}
	
	/**
	 * Create a point in the Fp field with the given parameters
	 * @return the created point
	 * @throws UnInitializedException 
	 */
	public ECElement getElement(BigInteger x, BigInteger y) throws UnInitializedException{
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		return new ECFpPointMiracl(x, y, this);
	}
	
	/**
	 * Check if the given element is member of that Dlog group
	 * @param element - 
	 * @return true if the given element is member of that group. false, otherwise.
	 * @throws UnInitializedException 
	 * @throws IllegalArgumentException
	 */
	public boolean isMember(GroupElement element) throws UnInitializedException {
		if (!isInitialized()){
			throw new UnInitializedException();
		}
		boolean member = false;
		//checks that the element is the correct object
		if(!(element instanceof ECFpPointMiracl)){
			throw new IllegalArgumentException("groupElement doesn't match the DlogGroup");
		}
		
		//infinity point is a valid member
		if (((ECFpPointMiracl) element).isInfinity()){
			return true;
		}
		
		//call for a native function that checks if the element is a point in this curve
		member = isFpMember(mip, ((ECFpPointMiracl) element).getPoint());	
		
		return member;
	}
	
	public ECElement getInfinity(){
		long infinity = createInfinityFpPoint(mip);
		return new ECFpPointMiracl(infinity, mip);
	}
	
	//upload MIRACL library
	static {
        System.loadLibrary("MiraclJavaInterface");
	}
	
}
